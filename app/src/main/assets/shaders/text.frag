precision mediump float;

uniform sampler2D u_Text;

varying vec2 v_TexCoord;


void main() {
    vec4 tempColor=texture2D(u_Text,v_TexCoord);
    vec4 textureColor = vec4(1.0, 1.0, 1.0, tempColor.a);
    gl_FragColor = textureColor;
}
