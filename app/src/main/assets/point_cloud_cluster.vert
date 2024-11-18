uniform mat4 u_ModelViewProjection;
uniform float u_PointSize;
uniform float u_ConfidenceThreshold;
uniform vec3 u_CameraPos;

attribute vec4 a_Position;

//varying vec4 v_Color;

float highBound = 5.0;
float lowBound = 0.0;

void main() {
    float distanceToCamera = length(a_Position.xyz - u_CameraPos);
    float clampedDistance = clamp(distanceToCamera, lowBound, highBound);
    //v_Color = vec4(vec3(highBound - clampedDistance + lowBound), 1.0);

    gl_Position = u_ModelViewProjection * vec4(a_Position.xyz, 1.0);

    // Set w of low confidence points to 0 to hide those points.
    gl_Position.w *= step(u_ConfidenceThreshold, a_Position.w);

    gl_PointSize = u_PointSize;
}