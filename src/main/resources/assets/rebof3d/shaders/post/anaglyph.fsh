#version 330
#extension GL_ARB_separate_shader_objects : require

// Anaglyph 立体合成片元着色器。
//
// Sampler0 = 左眼图像，Sampler1 = 右眼图像。
// 左眼乘「左滤光片颜色」，右眼乘「右滤光片颜色」，相加后再做饱和度与伽玛校正。
// 默认（左=青 (0,1,1)、右=红 (1,0,0)）即原版红青 anaglyph：
//   R 通道来自右眼，G+B 通道来自左眼。

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

// 由 Std140 打包的配置 UBO，字段顺序需与 Java 侧 Std140Builder 写入顺序严格一致。
layout(std140) uniform Reborn3DConfig {
    vec3 LeftFilter;   // 左眼滤光片颜色 RGB
    vec3 RightFilter;  // 右眼滤光片颜色 RGB
    float Gamma;       // 伽玛校准，输出 pow(c, 1/Gamma)
    float Saturation;  // 每眼饱和度增益
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

vec3 applySaturation(vec3 c, float s) {
    // 亮度近似，按饱和度在灰阶与彩色间插值
    float luma = dot(c, vec3(0.299, 0.587, 0.114));
    return mix(vec3(luma), c, s);
}

void main() {
    vec3 leftColor  = texture(Sampler0, texCoord).rgb;
    vec3 rightColor = texture(Sampler1, texCoord).rgb;

    leftColor  = applySaturation(leftColor,  Saturation);
    rightColor = applySaturation(rightColor, Saturation);

    // 各自乘滤光片颜色后叠加（通道分离合成）
    vec3 combined = leftColor * LeftFilter + rightColor * RightFilter;

    // 伽玛校正
    float invGamma = Gamma != 0.0 ? 1.0 / Gamma : 1.0;
    combined = pow(clamp(combined, 0.0, 1.0), vec3(invGamma));

    fragColor = vec4(combined, 1.0);
}
