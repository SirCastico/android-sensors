precision mediump float;

varying vec2 texcoords;
uniform sampler2D depthTex;

void main() {
    gl_FragColor = vec4(vec3(texture2D(depthTex, texcoords).r), 1.0);
}
