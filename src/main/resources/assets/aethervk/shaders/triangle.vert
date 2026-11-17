#version 450
layout(push_constant) uniform Push { float angle; float aspect; } pc;
layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec3 inColor;
layout(location = 0) out vec3 fragColor;
void main() {
    float c = cos(pc.angle);
    float s = sin(pc.angle);
    vec2 p = vec2(inPosition.x * c - inPosition.y * s,
                  inPosition.x * s + inPosition.y * c);
    p.x /= pc.aspect;
    gl_Position = vec4(p, 0.0, 1.0);
    fragColor = inColor;
}
