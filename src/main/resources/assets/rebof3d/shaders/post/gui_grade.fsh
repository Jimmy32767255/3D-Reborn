#version 330
#extension GL_ARB_separate_shader_objects : require

// GUI 校色片元着色器（anaglyph 下的界面颜色校准）。
//
// Sampler0 = 单独渲染的 GUI 图层（RGBA，透明底，预乘 alpha）。
// 处理流程严格对齐原版 1.12.2：在 anaglyph 开启时，原版对**所有纹理**
// （含 GUI 图集，见 cdt.c(int)）逐像素做「亮度→R、偏红→G、偏青→B」变换，
// 于是戴红青眼镜时 GUI 的亮度落在红片一只眼、色差落在青片一只眼，观感恢复。
//
// 这里把该变换移到「GUI 图层」这一层做，等价于原版按纹理预校正的效果，
// 同时叠加全局伽玛/饱和度，最后以预乘 alpha 混合回已合成的世界。

uniform sampler2D Sampler0;

// 与 Java 侧 Std140Builder 写入顺序严格一致（复用 anaglyph 的配置 UBO）。
layout(std140) uniform Reborn3DConfig {
    vec3 LeftFilter;   // 未使用（保留布局一致）
    vec3 RightFilter;  // 未使用
    float Gamma;       // 全局伽玛
    float Saturation;  // 全局饱和度
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

vec3 applySaturation(vec3 c, float s) {
    float luma = dot(c, vec3(0.299, 0.587, 0.114));
    return mix(vec3(luma), c, s);
}

// 原版 1.12.2 cdt.c(int)：R'=亮度(30/59/11)，G'=(R*30+G*70)/100，B'=(R*30+B*70)/100。
vec3 anaglyphGrade(vec3 c) {
    float r = (c.r * 30.0 + c.g * 59.0 + c.b * 11.0) / 100.0;
    float g = (c.r * 30.0 + c.g * 70.0) / 100.0;
    float b = (c.r * 30.0 + c.b * 70.0) / 100.0;
    return vec3(r, g, b);
}

void main() {
    vec4 s = texture(Sampler0, texCoord);
    float a = s.a;
    // 目标为预乘 alpha：先还原直通颜色，再做（定义在直通颜色上的）校色变换。
    vec3 straight = a > 0.0 ? s.rgb / a : vec3(0.0);

    vec3 graded = anaglyphGrade(straight);
    graded = applySaturation(graded, Saturation);

    float invGamma = Gamma != 0.0 ? 1.0 / Gamma : 1.0;
    graded = pow(clamp(graded, 0.0, 1.0), vec3(invGamma));

    // 重新预乘 alpha，配合 TRANSLUCENT_PREMULTIPLIED_ALPHA 混合回世界。
    fragColor = vec4(graded * a, a);
}
