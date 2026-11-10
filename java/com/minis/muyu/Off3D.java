package com.minis.muyu;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.Matrix;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * 离屏 3D 渲染：把 GLB 按「预览页最终视角」渲染成透明 PNG，
 * 作为桌面小部件与设置页预览的基底图 —— 预览定妆什么角度，桌面就是什么角度。
 */
public final class Off3D {

    private static final int SIZE = 1024;
    private static final String NAME = "fish3d.png";

    /** 与预览页完全相同的着色器（中性调色，预设颜色仍由 JadeRender 的 ColorMatrix 施加） */
    private static final String VS =
            "uniform mat4 uMVP;\n" +
            "uniform mat3 uNM;\n" +
            "attribute vec3 aPos;\n" +
            "attribute vec3 aNormal;\n" +
            "attribute vec3 aColor;\n" +
            "varying vec3 vN;\n" +
            "varying vec3 vC;\n" +
            "varying vec3 vP;\n" +
            "varying vec3 vObj;\n" +
            "void main() {\n" +
            "  vN = normalize(uNM * aNormal);\n" +
            "  vC = aColor;\n" +
            "  vec4 mv = uMVP * vec4(aPos, 1.0);\n" +
            "  vP = mv.xyz;\n" +
            "  gl_Position = uMVP * vec4(aPos, 1.0);\n" +
            "}\n";

    // 简化版：MVP 一路到底（法线矩阵够用即可），保持与预览一致的布光
    private static final String VS2 =
            "uniform mat3 uNM;\n" +
            "uniform mat4 uMVP;\n" +
            "uniform mat4 uMV;\n" +
            "attribute vec3 aPos;\n" +
            "attribute vec3 aNormal;\n" +
            "attribute vec3 aColor;\n" +
            "varying vec3 vN;\n" +
            "varying vec3 vC;\n" +
            "varying vec3 vP;\n" +
            "varying vec3 vObj;\n" +
            "void main() {\n" +
            "  vN = normalize(uNM * aNormal);\n" +
            "  vC = aColor;\n" +
            "  vP = (uMV * vec4(aPos, 1.0)).xyz;\n" +
            "  vObj = aPos;\n" +
            "  gl_Position = uMVP * vec4(aPos, 1.0);\n" +
            "}\n";

    private static final String FS =
            "precision mediump float;\n" +
            "uniform vec3 uMatTint;\n" +
            "uniform float uMat;\n" +
            "uniform float uRimK;\n" +
            "uniform vec3 uRimColor;\n" +
            "uniform float uSpecK;\n" +
            "uniform float uSpecPow;\n" +
            "uniform float uAlpha;\n" +
            "uniform float uPress;\n" +
            "uniform float uHasTex;\n" +
            "uniform sampler2D uWoodTex;\n" +
            "uniform sampler2D uAmberTex;\n" +
            "varying vec3 vN;\n" +
            "varying vec3 vC;\n" +
            "varying vec3 vP;\n" +
            "varying vec3 vObj;\n" +
            "void main() {\n" +
            "  vec3 N = normalize(vN);\n" +
            "  vec3 V = normalize(-vP);\n" +
            "  vec3 L1 = normalize(vec3(-0.45, 0.62, 0.64));\n" +
            "  vec3 L2 = normalize(vec3(0.55, -0.25, 0.35));\n" +
            "  vec3 base = vC * uMatTint;\n" +
            "  vec3 blend = pow(abs(N), vec3(4.0));\n" +
            "  blend /= max(vec3(1e-4), vec3(blend.x + blend.y + blend.z));\n" +
            "  vec3 warp = vec3(\n" +
            "      sin(vObj.y * 3.13 + vObj.z * 2.31),\n" +
            "      sin(vObj.z * 2.71 + vObj.x * 1.93),\n" +
            "      sin(vObj.x * 2.89 + vObj.y * 2.13)) * 0.075;\n" +
            "  vec3 p3 = (vObj + warp) * 0.30;\n" +
            "  vec2 uvX = vec2(p3.z * 0.94 - p3.y * 0.34, p3.y * 0.94 + p3.z * 0.34) + vec2(0.37, 0.53);\n" +
            "  vec2 uvY = vec2(p3.x * 0.87 - p3.z * 0.50, p3.z * 0.87 + p3.x * 0.50) + vec2(0.61, 0.42);\n" +
            "  vec2 uvZ = vec2(p3.x * 0.80 - p3.y * 0.60, p3.y * 0.80 + p3.x * 0.60) + vec2(0.45, 0.68);\n" +
            "  if (uMat > 0.5 && uMat < 1.5) {\n" +
            "    vec3 wood = texture2D(uWoodTex, uvX).rgb * blend.x\n" +
            "              + texture2D(uWoodTex, uvY).rgb * blend.y\n" +
            "              + texture2D(uWoodTex, uvZ).rgb * blend.z;\n" +
            "    wood = mix(wood, vec3(dot(wood, vec3(0.3333))), 0.15);\n" +          // 保留木色
            "    wood = clamp((wood - 0.46) * 1.35 + 0.52, 0.0, 1.0);\n" +           // 深纹拉深、浅纹提亮
            "    base *= mix(vec3(1.0), wood * 1.02, uHasTex * 0.94);\n" +

            "  } else if (uMat > 1.5) {\n" +
            "    vec3 amb = texture2D(uAmberTex, uvX * 0.85).rgb * blend.x\n" +
            "             + texture2D(uAmberTex, uvY * 0.85).rgb * blend.y\n" +
            "             + texture2D(uAmberTex, uvZ * 0.85).rgb * blend.z;\n" +
            "    base *= mix(vec3(1.0), amb * 1.10, uHasTex);\n" +
            "  }\n" +
            "  float wrap = 0.42;\n" +
            "  float d1 = clamp((dot(N, L1) + wrap) / (1.0 + wrap), 0.0, 1.0);\n" +
            "  float d2 = clamp((dot(N, L2) + wrap) / (1.0 + wrap), 0.0, 1.0);\n" +
            "  vec3 col = base * (0.20 + 0.82 * d1 + 0.18 * d2);\n" +
            "  vec3 H1 = normalize(L1 + V);\n" +
            "  float sp = pow(max(dot(N, H1), 0.0), uSpecPow) * uSpecK;\n" +
            "  col += vec3(sp);\n" +
            // 清漆：木表面一层广域镜面
            "  if (uMat > 0.5 && uMat < 1.5) {\n" +
            "    float coat = pow(max(dot(N, H1), 0.0), 8.0) * 0.06;\n" +
            "    col += vec3(coat);\n" +
            "  }\n" +
            "  float fres = 1.0 - clamp(dot(N, V), 0.0, 1.0);\n" +
            "  float rim = pow(fres, 2.6);\n" +
            "  float halo = pow(fres, 1.15);\n" +
            "  col += uRimColor * (rim * uRimK + halo * uRimK * 0.55);\n" +
            "  col *= (1.0 + uPress * 0.06);\n" +
            "  float ndv = clamp(dot(N, V), 0.0, 1.0);\n" +
            "  float a = mix(uAlpha * 0.90, min(1.0, uAlpha + 0.06), ndv);\n" +
            "  gl_FragColor = vec4(col, a);\n" +
            "}\n";

    private static final float[] BASE_COLOR = {0.949f, 0.918f, 0.851f};

    private Off3D() {}

    static File outFile(Context c) { return new File(c.getFilesDir(), NAME); }

    /**
     * 后台线程执行：按给定视角渲染并保存 base 图。
     * @return 是否成功
     */
    static boolean renderToFile(Context c, float yaw, float pitch, float zoom) {
        Bitmap bmp = render(c, yaw, pitch, zoom);
        if (bmp == null) return false;
        try {
            File f = outFile(c);
            FileOutputStream out = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
            bmp.recycle();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static Bitmap render(Context c, float yawDeg, float pitchDeg, float zoom) {
        GlbMesh mesh = GlbMesh.get(c);
        if (mesh == null) return null;

        EGLDisplay dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] ver = new int[2];
        if (!EGL14.eglInitialize(dpy, ver, 0, ver, 1)) return null;

        int[] cfgAttr = {
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 16,
                EGL14.EGL_NONE
        };
        EGLConfig[] cfg = new EGLConfig[1];
        int[] n = new int[1];
        if (!EGL14.eglChooseConfig(dpy, cfgAttr, 0, cfg, 0, 1, n, 0) || n[0] == 0) {
            EGL14.eglTerminate(dpy);
            return null;
        }
        int[] ctxAttr = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
        EGLContext ctx = EGL14.eglCreateContext(dpy, cfg[0], EGL14.EGL_NO_CONTEXT, ctxAttr, 0);
        int[] pbAttr = {EGL14.EGL_WIDTH, SIZE, EGL14.EGL_HEIGHT, SIZE, EGL14.EGL_NONE};
        EGLSurface surf = EGL14.eglCreatePbufferSurface(dpy, cfg[0], pbAttr, 0);
        if (!EGL14.eglMakeCurrent(dpy, surf, surf, ctx)) {
            EGL14.eglDestroySurface(dpy, surf);
            EGL14.eglDestroyContext(dpy, ctx);
            EGL14.eglTerminate(dpy);
            return null;
        }

        try {
            GLES20.glViewport(0, 0, SIZE, SIZE);
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glDepthFunc(GLES20.GL_LEQUAL);
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            GLES20.glCullFace(GLES20.GL_BACK);

            int prog = program();
            int aPos = GLES20.glGetAttribLocation(prog, "aPos");
            int aNor = GLES20.glGetAttribLocation(prog, "aNormal");
            int aCol = GLES20.glGetAttribLocation(prog, "aColor");
            int uMVP = GLES20.glGetUniformLocation(prog, "uMVP");
            int uMV = GLES20.glGetUniformLocation(prog, "uMV");
            int uNM = GLES20.glGetUniformLocation(prog, "uNM");
            int uMatTint = GLES20.glGetUniformLocation(prog, "uMatTint");
            int uMat = GLES20.glGetUniformLocation(prog, "uMat");
            int uRimK = GLES20.glGetUniformLocation(prog, "uRimK");
            int uRimColor = GLES20.glGetUniformLocation(prog, "uRimColor");
            int uSpecK = GLES20.glGetUniformLocation(prog, "uSpecK");
            int uSpecPow = GLES20.glGetUniformLocation(prog, "uSpecPow");
            int uAlpha = GLES20.glGetUniformLocation(prog, "uAlpha");
            int uWoodTex = GLES20.glGetUniformLocation(prog, "uWoodTex");
            int uAmberTex = GLES20.glGetUniformLocation(prog, "uAmberTex");
            int uHasTex = GLES20.glGetUniformLocation(prog, "uHasTex");

            int texWood = -1;
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeResource(
                    c.getResources(), R.drawable.wood);
            if (bmp != null) {
                int[] t = new int[1];
                GLES20.glGenTextures(1, t, 0);
                texWood = t[0];
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texWood);
                android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                        GLES20.GL_LINEAR_MIPMAP_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
                bmp.recycle();
            }
            int texAmber = -1;
            android.graphics.Bitmap abd = android.graphics.BitmapFactory.decodeResource(
                    c.getResources(), R.drawable.amber);
            if (abd != null) {
                int[] t2 = new int[1];
                GLES20.glGenTextures(1, t2, 0);
                texAmber = t2[0];
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texAmber);
                android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, abd, 0);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                        GLES20.GL_LINEAR_MIPMAP_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
                abd.recycle();
            }

            int[] vbo = new int[1];
            int[] ibo = new int[1];
            GLES20.glGenBuffers(1, vbo, 0);
            GLES20.glGenBuffers(1, ibo, 0);
            mesh.verts.position(0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0]);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mesh.verts.capacity() * 4, mesh.verts, GLES20.GL_STATIC_DRAW);
            mesh.indices.position(0);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo[0]);
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.indices.capacity() * 4, mesh.indices, GLES20.GL_STATIC_DRAW);

            float[] proj = new float[16];
            float[] view = new float[16];
            float[] model = new float[16];
            float[] mv = new float[16];
            float[] mvp = new float[16];
            float[] nm = new float[9];

            Matrix.perspectiveM(proj, 0, 38f, 1f, 0.1f, 20f);
            float s = mesh.radius;
            Matrix.setIdentityM(model, 0);
            Matrix.scaleM(model, 0, s, s, s);
            Matrix.translateM(model, 0, -mesh.centerX, -mesh.centerY, -mesh.centerZ);

            float tanHalf = (float) Math.tan(Math.toRadians(38f / 2.0));
            float baseDist = 0.62f / (tanHalf * 0.85f);
            float dist = baseDist * zoom;
            float cy = (float) Math.cos(Math.toRadians(pitchDeg));
            float sy = (float) Math.sin(Math.toRadians(pitchDeg));
            Matrix.setLookAtM(view, 0, 0f, dist * sy, dist * cy, 0f, 0f, 0f, 0f, 1f, 0f);
            Matrix.multiplyMM(mv, 0, view, 0, model, 0);
            Matrix.rotateM(mv, 0, yawDeg, 0f, 1f, 0f);
            Matrix.multiplyMM(mvp, 0, proj, 0, mv, 0);

            nm[0] = mv[0]; nm[1] = mv[1]; nm[2] = mv[2];
            nm[3] = mv[4]; nm[4] = mv[5]; nm[5] = mv[6];
            nm[6] = mv[8]; nm[7] = mv[9]; nm[8] = mv[10];

            GLES20.glUseProgram(prog);
            GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0);
            GLES20.glUniformMatrix4fv(uMV, 1, false, mv, 0);
            GLES20.glUniformMatrix3fv(uNM, 1, false, nm, 0);
            int mat = MuyuState.material(c);
            float tr, tg, tb, rimK, specK, specPow, rimR, rimG, rimB, alpha;
            if (mat == 1) {
                tr = MuyuState.woodR(c); tg = MuyuState.woodG(c); tb = MuyuState.woodB(c);
                rimK = 0.08f; specK = 0.22f; specPow = 48f; rimR = 0.62f; rimG = 0.48f; rimB = 0.32f; alpha = 1.00f;
            } else if (mat == 2) {
                tr = 1.00f; tg = 1.00f; tb = 1.00f;      // 蜜蜡：贴图带色，widget 侧不再乘色
                rimK = 0.26f; specK = 0.55f; specPow = 110f; rimR = 1.00f; rimG = 0.60f; rimB = 0.20f; alpha = 0.90f;
            } else {
                tr = 0.995f; tg = 0.935f; tb = 0.820f;   // 玉：暖白脂，烘焙进基底
                rimK = 0.32f; specK = 0.10f; specPow = 22f; rimR = 0.88f; rimG = 0.85f; rimB = 0.70f; alpha = 0.93f;
            }
            GLES20.glUniform3f(uMatTint, tr, tg, tb);
            GLES20.glUniform1f(uMat, mat);
            GLES20.glUniform1f(uRimK, rimK);
            GLES20.glUniform3f(uRimColor, rimR, rimG, rimB);
            GLES20.glUniform1f(uSpecK, specK);
            GLES20.glUniform1f(uSpecPow, specPow);
            GLES20.glUniform1f(uAlpha, alpha);
            if (texWood > 0) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texWood);
                GLES20.glUniform1i(uWoodTex, 0);
                GLES20.glUniform1f(uHasTex, 1f);
            } else {
                GLES20.glUniform1f(uHasTex, 0f);
            }
            if (texAmber > 0) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texAmber);
                GLES20.glUniform1i(uAmberTex, 1);
            }

            int stride = 9 * 4;
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0]);
            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, 0);
            GLES20.glEnableVertexAttribArray(aNor);
            GLES20.glVertexAttribPointer(aNor, 3, GLES20.GL_FLOAT, false, stride, 12);
            GLES20.glEnableVertexAttribArray(aCol);
            GLES20.glVertexAttribPointer(aCol, 3, GLES20.GL_FLOAT, false, stride, 24);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo[0]);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, GLES20.GL_UNSIGNED_INT, 0);

            ByteBuffer buf = ByteBuffer.allocateDirect(SIZE * SIZE * 4).order(ByteOrder.nativeOrder());
            buf.position(0);
            GLES20.glReadPixels(0, 0, SIZE, SIZE, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);

            int[] pixels = new int[SIZE * SIZE];
            buf.position(0);
            IntBuffer ib = buf.asIntBuffer();
            ib.get(pixels);
            // GL 是下→上，翻转行序
            int[] flip = new int[SIZE * SIZE];
            for (int y = 0; y < SIZE; y++) {
                System.arraycopy(pixels, y * SIZE, flip, (SIZE - 1 - y) * SIZE, SIZE);
            }
            Bitmap raw = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            raw.copyPixelsFromBuffer(IntBuffer.wrap(flip));
            return fitToBase(raw);
        } catch (Throwable t) {
            return null;
        } finally {
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(dpy, surf);
            EGL14.eglDestroyContext(dpy, ctx);
            EGL14.eglTerminate(dpy);
        }
    }

    /** 裁剪到 alpha 包围盒，等比装进 512×402 画布（与旧基底同尺寸，布局零变化） */
    private static Bitmap fitToBase(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = (px[y * w + x] >>> 24);
                if (a > 12) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) { src.recycle(); return null; }

        Bitmap crop = Bitmap.createBitmap(src, minX, minY, maxX - minX + 1, maxY - minY + 1);
        int W = 512, H = 402;
        float sc = Math.min((float) W / crop.getWidth(), (float) H / crop.getHeight());
        int nw = Math.round(crop.getWidth() * sc), nh = Math.round(crop.getHeight() * sc);
        Bitmap scaled = Bitmap.createScaledBitmap(crop, nw, nh, true);
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(out);
        cv.drawBitmap(scaled, (W - nw) / 2f, (H - nh) / 2f, null);
        src.recycle(); crop.recycle(); scaled.recycle();
        return out;
    }

    private static int program() {
        int v = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(v, VS2);
        GLES20.glCompileShader(v);
        int f = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(f, FS);
        GLES20.glCompileShader(f);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        return p;
    }
}
