# -*- coding: utf-8 -*-
"""把 shader 包成可在手机浏览器里跑的 WebGL2 预览页"""
import json, os

SHADER = "/var/minis/workspace/woodfish/shader/muyu.glsl"
OUT = "/var/minis/workspace/woodfish/preview/index.html"

frag = open(SHADER).read()
if frag.startswith("#version 300 es"):
    pass

html = """<!DOCTYPE html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<style>
html,body{margin:0;height:100%;background:#0d0a08;overflow:hidden}
canvas{display:block;width:100vw;height:100vh;touch-action:none}
#hud{position:fixed;left:0;right:0;bottom:14px;text-align:center;color:#8a6a4a;
     font:13px/1.4 sans-serif;pointer-events:none}
</style></head><body>
<canvas id="c"></canvas>
<div id="hud">拖动旋转 · 双击复位（预览模式）</div>
<script id="fs" type="x-shader/x-fragment">__FRAG__</script>
<script>
const canvas = document.getElementById('c');
const gl = canvas.getContext('webgl2', {antialias:false, alpha:false});
if(!gl){ document.body.innerHTML = '<p style="color:#f66;font:16px sans-serif;padding:20px">WebGL2 不可用</p>'; }
const vsSrc = `#version 300 es
in vec2 aPos;
void main(){ gl_Position = vec4(aPos, 0.0, 1.0); }`;
const fsSrc = document.getElementById('fs').textContent;
function sh(type, src){
  const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
  if(!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));
  return s;
}
let prog;
try {
  prog = gl.createProgram();
  gl.attachShader(prog, sh(gl.VERTEX_SHADER, vsSrc));
  gl.attachShader(prog, sh(gl.FRAGMENT_SHADER, fsSrc));
  gl.linkProgram(prog);
  if(!gl.getProgramParameter(prog, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(prog));
} catch(e){
  document.body.innerHTML = '<pre style="color:#f88;font:12px monospace;padding:12px;white-space:pre-wrap">' + e.message + '</pre>';
  throw e;
}
gl.useProgram(prog);
const buf = gl.createBuffer();
gl.bindBuffer(gl.ARRAY_BUFFER, buf);
gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1, 3,-1, -1,3]), gl.STATIC_DRAW);
const loc = gl.getAttribLocation(prog, 'aPos');
gl.enableVertexAttribArray(loc);
gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
const uRes = gl.getUniformLocation(prog, 'uRes');
const uYaw = gl.getUniformLocation(prog, 'uYaw');
const uPitch = gl.getUniformLocation(prog, 'uPitch');
const uKnock = gl.getUniformLocation(prog, 'uKnock');
const uChroma = gl.getUniformLocation(prog, 'uChroma');
const CHROMA = new URLSearchParams(location.search).has('chroma');

let yaw = 0.0, pitch = 0.10;  // 验证旋转用：侧 60°
let dragging = false, lx = 0, ly = 0, interacted = false;
canvas.addEventListener('pointerdown', e => { interacted = true; dragging = true; lx = e.clientX; ly = e.clientY; });
canvas.addEventListener('pointermove', e => {
  if(!dragging) return;
  yaw += (e.clientX - lx) * 0.012;
  pitch = Math.max(-1.1, Math.min(1.1, pitch + (e.clientY - ly) * 0.010));
  lx = e.clientX; ly = e.clientY;
});
canvas.addEventListener('pointerup', () => dragging = false);
canvas.addEventListener('pointercancel', () => dragging = false);
canvas.addEventListener('dblclick', () => { yaw = 0; pitch = 0.12; });

function resize(){
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  canvas.width = Math.floor(canvas.clientWidth * dpr);
  canvas.height = Math.floor(canvas.clientHeight * dpr);
  gl.viewport(0, 0, canvas.width, canvas.height);
}
window.addEventListener('resize', resize);
resize();

function frame(){
  // 松手缓慢回正（预览里跟 App 手感一致）
  if(!dragging && interacted){
    yaw += (0 - yaw) * 0.02;
    pitch += (0.12 - pitch) * 0.02;
  }
  gl.uniform2f(uRes, canvas.width, canvas.height);
  gl.uniform1f(uYaw, yaw);
  gl.uniform1f(uPitch, -pitch);
  gl.uniform1f(uKnock, 0.0);
  gl.uniform1f(uChroma, CHROMA ? 1.0 : 0.0);
  gl.drawArrays(gl.TRIANGLES, 0, 3);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
</script></body></html>"""

html = html.replace("__FRAG__", frag.strip())
open(OUT, "w").write(html)
print("preview written", len(html))
