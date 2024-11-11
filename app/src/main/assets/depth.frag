varying vec2 texcoords;

uniform sampler2D depthTex;

void main() {
    gl_FragColor = vec4(vec3(texture(depthTex, texcoords)), 1.0);
}
