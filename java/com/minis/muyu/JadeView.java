package com.minis.muyu;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 可旋转的 3D 木鱼视图，两处复用：
 *  - 设置页小窗（autoSpin = false，按需重绘省电）
 *  - 全屏预览（autoSpin = true，连续渲染 + 静置慢转）
 * 双击回调给宿主（小窗 → 打开全屏）。
 */
public class JadeView extends GLSurfaceView implements GLSurfaceView.Renderer {

    public interface Listener {
        void onDoubleTap();
        /** 交互静止（拖完/缩放完 700ms）——宿主可据此预渲染小部件基底图 */
        void onSettled(float yaw, float pitch, float zoom);
    }

    private static final String VS =
            "uniform mat4 uMVP;\n" +
            "uniform mat4 uMV;\n" +
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

    private final boolean autoSpin;
    private Listener listener;

    private GlbMesh mesh;
    private int prog, aPos, aNormal, aColor, uMVP, uMV, uNM, uMatTint, uMat, uRimK, uRimColor, uSpecK, uSpecPow, uAlpha, uPress, uWoodTex, uAmberTex, uHasTex;
    private int texWood = -1, texAmber = -1;
    private int vbo = -1, ibo = -1;
    private boolean needUpload;

    private float yaw, pitch, zoom = 1f, targetZoom = 1f;
    private float vyaw, vpitch;
    private float press, targetPress;
    private boolean dragging;
    private long lastTouchAt;
    private boolean settledFired;

    private float baseDist = 4.85f;   // 由视口宽高比计算，保证鱼占画面约 85%
    private static final float FOV = 38f;
    private final float[] proj = new float[16];
    private final float[] viewM = new float[16];
    private final float[] model = new float[16];
    private final float[] mv = new float[16];
    private final float[] mvp = new float[16];
    private final float[] nm = new float[9];
    private final float[] tmp3 = new float[9];
    private final float[] inv = new float[9];

    private final GestureDetector gestures;

    public JadeView(Context c) { this(c, false); }

    public JadeView(Context c, boolean autoSpin) {
        this(c, null, autoSpin);
    }

    public JadeView(Context c, AttributeSet attrs) {
        this(c, attrs, false);
    }

    private JadeView(Context c, AttributeSet attrs, boolean autoSpin) {
        super(c, attrs);
        this.autoSpin = autoSpin;
        yaw = MuyuState.viewYaw(c);
        pitch = MuyuState.viewPitch(c);
        zoom = targetZoom = 1f;   // 缩放不跨会话持久，避免上次捏合导致取景被裁

        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(autoSpin ? RENDERMODE_CONTINUOUSLY : RENDERMODE_WHEN_DIRTY);
        setZOrderOnTop(false);

        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (listener != null) listener.onDoubleTap();
                return true;
            }
        });

        loadMeshAsync();
    }

    public void setListener(Listener l) { this.listener = l; }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.VISIBLE) onResume();
        else onPause();
    }

    @Override
    protected void onDetachedFromWindow() {
        onPause();
        super.onDetachedFromWindow();
    }

    private void loadMeshAsync() {
        final Context ctx = getContext().getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                final GlbMesh m = GlbMesh.get(ctx);
                queueEvent(new Runnable() {
                    @Override public void run() { mesh = m; needUpload = true; requestRender(); }
                });
            }
        }, "glb-load").start();
    }

    /* ---------------- 交互 ---------------- */

    private float lastX, lastY, lastSpan;
    private boolean multi;

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        gestures.onTouchEvent(e);
        getParent().requestDisallowInterceptTouchEvent(true);

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX(); lastY = e.getY();
                dragging = true; vyaw = 0; vpitch = 0;
                multi = false;
                settledFired = false;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                multi = true;
                lastSpan = span(e);
                dragging = false;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                multi = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (multi || e.getPointerCount() > 1) {
                    float s = span(e);
                    if (lastSpan > 0 && s > 0) {
                        targetZoom = clamp(targetZoom * (lastSpan / s), 0.55f, 2.4f);
                        zoom += (targetZoom - zoom) * 0.5f;
                    }
                    lastSpan = s;
                } else {
                    float dx = e.getX() - lastX, dy = e.getY() - lastY;
                    float ry = dx * 0.26f, rp = dy * 0.22f;
                    yaw += ry;
                    pitch = clamp(pitch + rp, -80f, 80f);
                    vyaw = vyaw * 0.40f + ry * 0.60f;
                    vpitch = vpitch * 0.40f + rp * 0.60f;
                    lastX = e.getX(); lastY = e.getY();
                    requestRender();
                }
                lastTouchAt = System.currentTimeMillis();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                lastTouchAt = System.currentTimeMillis();
                startInertia();
                break;
        }
        return true;
    }

    private float span(MotionEvent e) {
        if (e.getPointerCount() < 2) return 0;
        return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
    }

    private void startInertia() {
        postOnAnimation(new Runnable() {
            @Override public void run() {
                if (dragging) return;
                if (Math.abs(vyaw) > 0.15f || Math.abs(vpitch) > 0.15f) {
                    yaw += vyaw;
                    pitch = clamp(pitch + vpitch, -80f, 80f);
                    vyaw *= 0.93f; vpitch *= 0.93f;
                    requestRender();
                    postOnAnimation(this);
                } else {
                    vyaw = 0; vpitch = 0;
                    requestRender();
                    scheduleSettled();
                }
            }
        });
    }

    private void scheduleSettled() {
        if (settledFired) return;
        postDelayed(new Runnable() {
            @Override public void run() {
                if (dragging) return;
                if (System.currentTimeMillis() - lastTouchAt < 650) return;
                settledFired = true;
                if (listener != null) {
                    listener.onSettled(MuyuState.viewYaw(getContext()) == yaw ? yaw : yaw, pitch, zoom);
                }
            }
        }, 700);
    }

    public void punch() {
        press = 1f;
        postOnAnimation(new Runnable() {
            @Override public void run() {
                press *= 0.86f;
                if (press < 0.01f) { press = 0f; requestRender(); return; }
                requestRender();
                postOnAnimation(this);
            }
        });
    }

    /* ---------------- 渲染 ---------------- */

    @Override public void onSurfaceCreated(GL10 g, EGLConfig cfg) {
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);

        prog = build();
        aPos = GLES20.glGetAttribLocation(prog, "aPos");
        aNormal = GLES20.glGetAttribLocation(prog, "aNormal");
        aColor = GLES20.glGetAttribLocation(prog, "aColor");
        uMVP = GLES20.glGetUniformLocation(prog, "uMVP");
        uMV = GLES20.glGetUniformLocation(prog, "uMV");
        uNM = GLES20.glGetUniformLocation(prog, "uNM");        uMatTint = GLES20.glGetUniformLocation(prog, "uMatTint");
        uMat = GLES20.glGetUniformLocation(prog, "uMat");
        uRimK = GLES20.glGetUniformLocation(prog, "uRimK");
        uRimColor = GLES20.glGetUniformLocation(prog, "uRimColor");
        uSpecK = GLES20.glGetUniformLocation(prog, "uSpecK");
        uSpecPow = GLES20.glGetUniformLocation(prog, "uSpecPow");
        uAlpha = GLES20.glGetUniformLocation(prog, "uAlpha");
        uPress = GLES20.glGetUniformLocation(prog, "uPress");
        uWoodTex = GLES20.glGetUniformLocation(prog, "uWoodTex");
        uAmberTex = GLES20.glGetUniformLocation(prog, "uAmberTex");
        uHasTex = GLES20.glGetUniformLocation(prog, "uHasTex");

        // 木纹贴图（REPEAT + mipmap，三平面映射会重复采样）
        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeResource(
                getResources(), R.drawable.wood);
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
        android.graphics.Bitmap abd = android.graphics.BitmapFactory.decodeResource(
                getResources(), R.drawable.amber);
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
        needUpload = true;
    }

    private int build() {
        int v = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(v, VS);
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

    @Override public void onSurfaceChanged(GL10 g, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
        float aspect = (float) w / Math.max(1, h);
        Matrix.perspectiveM(proj, 0, FOV, aspect, 0.1f, 20f);
        // 让归一化模型（外接球半径约 0.62）占画面最短边的 85%
        float tanHalfY = (float) Math.tan(Math.toRadians(FOV / 2.0));
        float tanMin = Math.min(tanHalfY, tanHalfY * aspect);
        baseDist = 0.62f / (tanMin * 0.85f);
    }

    @Override public void onDrawFrame(GL10 g) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (mesh == null) return;
        if (needUpload) { upload(); needUpload = false; }

        if (autoSpin && !dragging && Math.abs(vyaw) < 0.15f && Math.abs(vpitch) < 0.15f
                && System.currentTimeMillis() - lastTouchAt > 2600) {
            yaw += 0.10f;
        }

        float s = mesh.radius;
        Matrix.setIdentityM(model, 0);
        Matrix.scaleM(model, 0, s, s * (1f - press * 0.045f), s);
        Matrix.translateM(model, 0, -mesh.centerX, -mesh.centerY, -mesh.centerZ);

        float dist = baseDist * zoom;
        float cy = (float) Math.cos(Math.toRadians(pitch));
        float sy = (float) Math.sin(Math.toRadians(pitch));
        Matrix.setLookAtM(viewM, 0, 0f, dist * sy, dist * cy, 0f, 0f, 0f, 0f, 1f, 0f);
        Matrix.multiplyMM(mv, 0, viewM, 0, model, 0);
        Matrix.rotateM(mv, 0, yaw, 0f, 1f, 0f);
        Matrix.multiplyMM(mvp, 0, proj, 0, mv, 0);

        tmp3[0] = mv[0]; tmp3[1] = mv[1]; tmp3[2] = mv[2];
        tmp3[3] = mv[4]; tmp3[4] = mv[5]; tmp3[5] = mv[6];
        tmp3[6] = mv[8]; tmp3[7] = mv[9]; tmp3[8] = mv[10];
        float det = tmp3[0]*(tmp3[4]*tmp3[8]-tmp3[5]*tmp3[7])
                - tmp3[1]*(tmp3[3]*tmp3[8]-tmp3[5]*tmp3[6])
                + tmp3[2]*(tmp3[3]*tmp3[7]-tmp3[4]*tmp3[6]);
        if (Math.abs(det) > 1e-9f) {
            float id = 1f / det;
            inv[0] = (tmp3[4]*tmp3[8]-tmp3[5]*tmp3[7]) * id;
            inv[1] = (tmp3[2]*tmp3[7]-tmp3[1]*tmp3[8]) * id;
            inv[2] = (tmp3[1]*tmp3[5]-tmp3[2]*tmp3[4]) * id;
            inv[3] = (tmp3[5]*tmp3[6]-tmp3[3]*tmp3[8]) * id;
            inv[4] = (tmp3[0]*tmp3[8]-tmp3[2]*tmp3[6]) * id;
            inv[5] = (tmp3[2]*tmp3[3]-tmp3[0]*tmp3[5]) * id;
            inv[6] = (tmp3[3]*tmp3[7]-tmp3[4]*tmp3[6]) * id;
            inv[7] = (tmp3[1]*tmp3[6]-tmp3[0]*tmp3[7]) * id;
            inv[8] = (tmp3[0]*tmp3[4]-tmp3[1]*tmp3[3]) * id;
            nm[0] = inv[0]; nm[1] = inv[3]; nm[2] = inv[6];
            nm[3] = inv[1]; nm[4] = inv[4]; nm[5] = inv[7];
            nm[6] = inv[2]; nm[7] = inv[5]; nm[8] = inv[8];
        } else {
            nm[0] = 1; nm[4] = 1; nm[8] = 1;
        }

        GLES20.glUseProgram(prog);
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0);
        GLES20.glUniformMatrix4fv(uMV, 1, false, mv, 0);
        GLES20.glUniformMatrix3fv(uNM, 1, false, nm, 0);        int mat = MuyuState.material(getContext());
        float tr, tg, tb, rimK, specK, specPow, rimR, rimG, rimB, alpha;
        if (mat == 1) {
            tr = MuyuState.woodR(getContext()); tg = MuyuState.woodG(getContext()); tb = MuyuState.woodB(getContext());
            rimK = 0.08f; specK = 0.22f; specPow = 48f; rimR = 0.62f; rimG = 0.48f; rimB = 0.32f; alpha = 1.00f;
        } else if (mat == 2) {
            tr = 1.00f; tg = 1.00f; tb = 1.00f;   // 蜜蜡：贴图带色
            rimK = 0.26f; specK = 0.55f; specPow = 110f; rimR = 1.00f; rimG = 0.60f; rimB = 0.20f; alpha = 0.90f;
        } else {
            tr = 0.995f; tg = 0.935f; tb = 0.820f;
            rimK = 0.32f; specK = 0.10f; specPow = 22f; rimR = 0.88f; rimG = 0.85f; rimB = 0.70f; alpha = 0.93f;
        }
        GLES20.glUniform3f(uMatTint, tr, tg, tb);
        GLES20.glUniform1f(uMat, mat);
        GLES20.glUniform1f(uRimK, rimK);
        GLES20.glUniform3f(uRimColor, rimR, rimG, rimB);
        GLES20.glUniform1f(uSpecK, specK);
        GLES20.glUniform1f(uSpecPow, specPow);
        GLES20.glUniform1f(uPress, press);
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
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glEnableVertexAttribArray(aNormal);
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, stride, 12);
        GLES20.glEnableVertexAttribArray(aColor);
        GLES20.glVertexAttribPointer(aColor, 3, GLES20.GL_FLOAT, false, stride, 24);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, GLES20.GL_UNSIGNED_INT, 0);
    }

    private void upload() {
        int[] bufs = new int[2];
        GLES20.glGenBuffers(2, bufs, 0);
        vbo = bufs[0]; ibo = bufs[1];
        mesh.verts.position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mesh.verts.capacity() * 4, mesh.verts, GLES20.GL_STATIC_DRAW);
        mesh.indices.position(0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.indices.capacity() * 4, mesh.indices, GLES20.GL_STATIC_DRAW);
    }

    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public float getZoom() { return zoom; }

    public void applyView(float y, float p, float z) {
        yaw = y; pitch = p; zoom = targetZoom = z;
        requestRender();
    }

    private static float clamp(float v, float a, float b) { return v < a ? a : (v > b ? b : v); }
}
