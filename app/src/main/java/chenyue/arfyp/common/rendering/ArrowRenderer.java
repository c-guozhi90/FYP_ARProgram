package chenyue.arfyp.common.rendering;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import chenyue.arfyp.common.informationUtil.InformationManager;

/* we cannot determine the text coordinates directly.
 * The coordinates should be estimated by the anchor which will be returned by the ARCore.
 * we start rendering text at the anchor with no adjustment. And the Anchor is always in the
 * center of the recognized image.Indeed, this may cause visual discomfort. But this is the
 * easiest way to do the stuff.
 * */
public class ArrowRenderer {
    private static final int BYTE_PER_FLOAT = 4;
    private static final int TEXTCOORD_NUM = 2;
    private static final int VERTEX_COORD_NUM = 2;


    private static final String TAG = ArrowRenderer.class.getSimpleName();
    private static final String VERTEX_SHADER_NAME = "shaders/arrow.vert";
    private static final String FRAMENGT_SHADER_NAME = "shaders/arrow.frag";

    private int texCoordAttribute;
    private int positionAttribute;
    private int modelUniform;
    private final float[] modelMatrix = new float[16];
    private final float[] rotatedMatirx = new float[16];
    private int program;
    private final int[] textureId = new int[1];
    private final float[] VOA = {
            // x    y       u   v
            -0.5f, -0.3f, 0.0f, 0.0f,   // bottom-left
            -0.5f, -0.1f, 0.0f, 1.0f,   // top-left
            0.5f, -0.1f, 1.0f, 1.0f,    // top-right
            0.5f, -0.1f, 1.0f, 1.0f,    // top-right
            -0.5f, -0.1f, 0.0f, 1.0f,   // top-left
            0.5f, -0.3f, 1.0f, 0.0f};   // bottom-right

    public ArrowRenderer() {
    }

    public void createOnTread(Context context) throws IOException {
        // load shaders
        final int vertextShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
        final int fragmentShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, FRAMENGT_SHADER_NAME);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertextShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        GLES20.glUseProgram(program);
        ShaderUtil.checkGLError(TAG, "Program creation");

        // locate program parameters
        modelUniform = GLES20.glGetUniformLocation(program, "u_Model");
        positionAttribute = GLES20.glGetAttribLocation(program, "a_Position");
        texCoordAttribute = GLES20.glGetAttribLocation(program, "a_TexCoord");
        ShaderUtil.checkGLError(TAG, "Program parameters");

        // generate texture
        ShaderUtil.checkGLError(TAG, "texture loading");

        GLES20.glGenTextures(textureId.length, textureId, 0);
        Bitmap arrowTexture = BitmapFactory.decodeStream(context.getAssets().open("models/arrow.png"));
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0]);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, arrowTexture, 0);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        ShaderUtil.checkGLError(TAG, "texture loaded");

        Matrix.setIdentityM(this.modelMatrix, 0);
    }

    public void updateModelMatrix(float[] modelMatrix, float scaleFactor) {
        float[] scaleMatrix = new float[16];
        Matrix.setIdentityM(scaleMatrix, 0);
        scaleMatrix[0] = scaleFactor;
        scaleMatrix[5] = scaleFactor;
        scaleMatrix[10] = scaleFactor;
        Matrix.multiplyMM(this.modelMatrix, 0, modelMatrix, 0, scaleMatrix, 0);
    }

    public void updateModelMatrix(float rotatedAngle) {
        Matrix.rotateM(rotatedMatirx, 0, modelMatrix, 0, rotatedAngle, 0, 0, -1);
    }

    public void draw(double rotationAngle) {

        // clear color in buffer
//        GLES20.glClearColor(1, 1, 1, 1);
//        GLES20.glColorMask(false, false, false, true);
//        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
//        GLES20.glColorMask(true, true, true, true);

        GLES20.glUseProgram(program);
        ShaderUtil.checkGLError(TAG, "Before draw");

        // set matrix into shader
        GLES20.glUniformMatrix4fv(modelUniform, 1, false, modelMatrix, 0);

        // set render mode
        GLES20.glDepthMask(false);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA); // set with another color
        // begin draw

        FloatBuffer verticesBuffer = ByteBuffer.allocateDirect(VOA.length * BYTE_PER_FLOAT).order(ByteOrder.nativeOrder()).asFloatBuffer();
        verticesBuffer.put(VOA).position(0);
        GLES20.glVertexAttribPointer(positionAttribute, VERTEX_COORD_NUM * BYTE_PER_FLOAT, GLES20.GL_FLOAT, false, 4 * BYTE_PER_FLOAT, verticesBuffer);
        verticesBuffer.position(VERTEX_COORD_NUM);
        GLES20.glEnableVertexAttribArray(positionAttribute);
        GLES20.glVertexAttribPointer(texCoordAttribute, TEXTCOORD_NUM * BYTE_PER_FLOAT, GLES20.GL_FLOAT, false, 4 * BYTE_PER_FLOAT, verticesBuffer);
        GLES20.glEnableVertexAttribArray(texCoordAttribute);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisableVertexAttribArray(positionAttribute);
        GLES20.glDisableVertexAttribArray(texCoordAttribute);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        ShaderUtil.checkGLError(TAG, "after draw");
    }

}
