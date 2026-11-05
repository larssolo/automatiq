/* Ambient shader gradient behind every page, after the app's own background: soft, flowing fields of
   green, teal and a hint of amber, kept dark so text stays readable. Scroll position steers the colours
   and the flow; scroll speed stirs it. Plain WebGL1, rendered at low resolution (it is all gradients). */
(() => {
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const cv = document.createElement('canvas');
  cv.className = 'shader-bg'; cv.setAttribute('aria-hidden', 'true');
  const gl = cv.getContext('webgl', {alpha: false, antialias: false, depth: false, powerPreference: 'low-power'});
  if (!gl) return;

  const vs = 'attribute vec2 p;void main(){gl_Position=vec4(p,0.,1.);}';
  const fs = `precision mediump float;
uniform vec2 uRes; uniform float uT, uS, uV;
// simplex noise (Ashima / Stefan Gustavson, MIT)
vec3 m289(vec3 x){return x-floor(x*(1./289.))*289.;} vec2 m289(vec2 x){return x-floor(x*(1./289.))*289.;}
vec3 perm(vec3 x){return m289(((x*34.)+1.)*x);}
float sn(vec2 v){const vec4 C=vec4(.211324865405187,.366025403784439,-.577350269189626,.024390243902439);
 vec2 i=floor(v+dot(v,C.yy)),x0=v-i+dot(i,C.xx),i1=(x0.x>x0.y)?vec2(1.,0.):vec2(0.,1.);vec4 x12=x0.xyxy+C.xxzz;x12.xy-=i1;i=m289(i);
 vec3 p=perm(perm(i.y+vec3(0.,i1.y,1.))+i.x+vec3(0.,i1.x,1.));vec3 m=max(.5-vec3(dot(x0,x0),dot(x12.xy,x12.xy),dot(x12.zw,x12.zw)),0.);m=m*m;m=m*m;
 vec3 x=2.*fract(p*C.www)-1.,h=abs(x)-.5,ox=floor(x+.5),a0=x-ox;m*=1.79284291400159-.85373472095314*(a0*a0+h*h);
 vec3 g;g.x=a0.x*x0.x+h.x*x0.y;g.yz=a0.yz*x12.xz+h.yz*x12.yw;return 130.*dot(m,g);}
void main(){
 vec2 uv=gl_FragCoord.xy/uRes; vec2 q=vec2(uv.x*uRes.x/uRes.y,uv.y);
 float t=uT*.045+uS*1.6;
 // domain-warped flow: two layers of noise bend the coordinates like folded silk
 vec2 w=vec2(sn(q*.9+vec2(t,-t*.7)),sn(q*.9+vec2(-t*.6,t+3.1)));
 vec2 r=q+w*(.55+uV*.5);
 float n1=sn(r*1.1+vec2(t*.8,uS*2.));
 float n2=sn(r*.7-vec2(uS*1.4,t*.5)+7.3);
 float n3=sn(r*1.6+vec2(4.2,t));
 // palette from the app: deep green, robot teal, a touch of amber; scroll moves the balance
 vec3 base=vec3(.030,.046,.040);
 vec3 green=vec3(0.,.55,.26), teal=vec3(.05,.50,.50), amber=vec3(.70,.44,0.), indigo=vec3(.22,.24,.72);
 float s=uS;
 vec3 a=mix(green,teal,smoothstep(.15,.55,s)); a=mix(a,green,smoothstep(.7,1.,s));
 vec3 b=mix(teal,indigo,smoothstep(.3,.7,s)*.55);
 vec3 col=base;
 col+=a*smoothstep(-.15,.9,n1)*.32;
 col+=b*smoothstep(0.,1.,n2)*.24;
 col+=amber*smoothstep(.35,1.,n3)*(.07+.06*smoothstep(.45,.6,s)*(1.-smoothstep(.6,.75,s)));
 // keep the reading column darkest: a soft vignette towards the left and the bottom
 col*=mix(.68,1.,smoothstep(.05,.75,uv.x))*mix(.8,1.,smoothstep(0.,.5,uv.y));
 gl_FragColor=vec4(col,1.);
}`;
  const sh = (type, src) => { const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s); return gl.getShaderParameter(s, gl.COMPILE_STATUS) ? s : null; };
  const v = sh(gl.VERTEX_SHADER, vs), f = sh(gl.FRAGMENT_SHADER, fs);
  if (!v || !f) return;
  const pr = gl.createProgram(); gl.attachShader(pr, v); gl.attachShader(pr, f); gl.linkProgram(pr);
  if (!gl.getProgramParameter(pr, gl.LINK_STATUS)) return;
  gl.useProgram(pr);
  gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer());
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
  const loc = gl.getAttribLocation(pr, 'p'); gl.enableVertexAttribArray(loc); gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
  const U = n => gl.getUniformLocation(pr, n), uRes = U('uRes'), uT = U('uT'), uS = U('uS'), uV = U('uV');

  const SCALE = .32;                                          // smooth gradients need few pixels; CSS scales it up
  const size = () => { cv.width = Math.max(2, Math.round(innerWidth * SCALE)); cv.height = Math.max(2, Math.round(innerHeight * SCALE)); gl.viewport(0, 0, cv.width, cv.height); gl.uniform2f(uRes, cv.width, cv.height); };
  size(); addEventListener('resize', size);
  document.body.prepend(cv);
  document.documentElement.classList.add('has-shader-bg');

  let s = 0, sTarget = 0, vel = 0, lastY = scrollY, t0 = performance.now(), dirty = true;
  const prog = () => { const max = document.documentElement.scrollHeight - innerHeight; return max > 0 ? scrollY / max : 0; };
  addEventListener('scroll', () => { sTarget = prog(); vel = Math.min(1, vel + Math.abs(scrollY - lastY) / 900); lastY = scrollY; dirty = true; }, {passive: true});
  sTarget = s = prog();

  function draw(now) {
    s += (sTarget - s) * .08; vel *= .94;
    gl.uniform1f(uT, reduce ? 0 : (now - t0) / 1000); gl.uniform1f(uS, s); gl.uniform1f(uV, reduce ? 0 : vel);
    gl.drawArrays(gl.TRIANGLES, 0, 3);
  }
  let last = 0;
  (function loop(now) {
    requestAnimationFrame(loop);
    if (document.hidden) return;
    if (reduce) { if (dirty || Math.abs(sTarget - s) > .0005) { draw(now); dirty = false; } return; }
    if (now - last < 33) return;                              // ~30 fps is plenty for slow fields
    last = now; draw(now);
  })(0);
})();
