#version 330
#extension GL_ARB_separate_shader_objects : require

// 全屏三角形顶点着色器：与 Minecraft 自带 core/screenquad.vsh 等价。
// 用 gl_VertexIndex 生成覆盖整个 NDC 的单个大三角形（顶点 0,1,2）。
layout(location = 0) out vec2 texCoord;

void main() {
    vec2 uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    vec4 pos = vec4(uv * vec2(2, 2) + vec2(-1, -1), 0, 1);

    gl_Position = pos;
    texCoord = uv;
}
