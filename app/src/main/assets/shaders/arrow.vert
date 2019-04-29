uniform mat4 u_Model;

attribute vec2 a_Position;
attribute vec2 a_TexCoord;

varying vec2 v_TexCoord;

void main() {
    v_TexCoord = a_TexCoord;
    vec4 temp_Position=vec4(a_Position, 0.0, 1.0);
    gl_Position = u_Model*temp_Position;
}
