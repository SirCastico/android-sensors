uniform mat4 u_ModelViewProjection;
uniform float u_PointSize;
uniform float u_ConfidenceThreshold;

attribute vec4 a_Position;
//attribute vec3 a_Color;

varying vec4 v_Color;

void main() {
   //v_Color = vec4(a_Color, 1.0);
   gl_Position = u_ModelViewProjection * vec4(a_Position.xyz, 1.0);

   // Set w of low confidence points to 0 to hide those points.
   gl_Position.w *= step(u_ConfidenceThreshold, a_Position.w);

   gl_PointSize = u_PointSize;
}