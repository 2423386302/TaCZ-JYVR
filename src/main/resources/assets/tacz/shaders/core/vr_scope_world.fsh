#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    // Scene RGB is the complete world picture. Background alpha is cleared
    // to zero by FogRenderer; it describes neither light nor lens coverage.
    vec3 scene = texture(Sampler0, texCoord0).rgb;
    fragColor = vec4(scene * vertexColor.rgb * ColorModulator.rgb, 1.0);
}
