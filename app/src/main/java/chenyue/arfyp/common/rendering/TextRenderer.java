package chenyue.arfyp.common.rendering;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import chenyue.arfyp.common.freetype.FreetypeJNI;
import chenyue.arfyp.common.informationUtil.InformationManager;
import chenyue.arfyp.helloar.HelloArActivity;

public class TextRenderer {
    private static final int BYTE_PER_FLOAT = 4;
    private static final int TEXTCOORD_NUM = 2;
    private static final int VERTEX_COORD_NUM = 3;

    /* we cannot determine the text coordinates directly.
     * The coordinates should be estimated by the anchor which will be returned by the ARCore.
     * we start rendering text at the anchor with no adjustment. Indeed, this may cause visual
     * discomfort. But this is the easiest way to do the stuff.
     * */
    private static final String TAG = TextRenderer.class.getSimpleName();
    private static final String VERTEX_SHADER_NAME = "shaders/text.vert";
    private static final String FRAMENGT_SHADER_NAME = "shaders/text.frag";

    private int texCoordAttribute;
    private int positionAttribute;
    private int modelViewProjectionUniform;
    private final float[] modelMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];
    private static FreetypeJNI[] charBitmaps;
    private int program;
    private final int[] textures = new int[1];

    public TextRenderer() {
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
        modelViewProjectionUniform = GLES20.glGetUniformLocation(program, "u_ModelViewProjection");
        positionAttribute = GLES20.glGetAttribLocation(program, "a_Position");
        texCoordAttribute = GLES20.glGetAttribLocation(program, "a_TexCoord");
        ShaderUtil.checkGLError(TAG, "Program parameters");

        // read the text bitmap from JNI into memory
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
        charBitmaps = FreetypeJNI.initCharBitmaps(context);
        for (int c = 0; c < 128; c++) {
            GLES20.glGenTextures(textures.length, textures, 0);
            charBitmaps[c].setTextureId(textures[0]);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, charBitmaps[c].getTextureId());
            ByteBuffer charBitmapBuffer = ByteBuffer.allocateDirect(charBitmaps[c].getBitmap().length).order(ByteOrder.nativeOrder());
            charBitmapBuffer.put(charBitmaps[c].getBitmap()).position(0);
            int width = charBitmaps[c].getWidth();
            int height = charBitmaps[c].getHeight();
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_ALPHA, width, height, 0, GLES20.GL_ALPHA, GLES20.GL_UNSIGNED_BYTE, charBitmapBuffer);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        ShaderUtil.checkGLError(TAG, "texture loading");

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

    public void draw(float[] cameraView, float[] cameraPerspective, InformationManager informationManager) {
        float[] originPos = {-0.6f, 0.6f}; // the first elem is x-pos, the second is z-pos
        GLES20.glUseProgram(program);
        ShaderUtil.checkGLError(TAG, "Before draw");
        Matrix.multiplyMM(modelViewMatrix, 0, cameraView, 0, modelMatrix, 0);
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, cameraPerspective, 0, modelViewMatrix, 0);

        // bind MVPMatrix
        GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjectionMatrix, 0);

        //GLES20.glEnable(GLES20.GL_BLEND);
        //GLES20.glBlendFunc(GLES20.GL_ZERO, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        // set every char coordinates and draw
        synchronized (informationManager.getInformationArray()) {
            String facilityName = informationManager.getFacilityName();
            // draw the facility name
            drawSentence(facilityName, originPos, 1.0f);

            // draw events info only when they are expended
            if (informationManager.isExpended()) {
                originPos[0] += 0.2f;
                ArrayList<String> events = informationManager.getInformationArray();

                // draw the evens holding in facility
                for (String event : events) {
                    drawSentence(event, originPos, 0.5f);
                    originPos[1] -= 0.3f;
                }
            }
        }
        //GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisableVertexAttribArray(positionAttribute);
        GLES20.glDisableVertexAttribArray(texCoordAttribute);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        ShaderUtil.checkGLError(TAG, "after draw");
    }

    private void drawSentence(String sentence, float[] originPos, float scaleFactor) {
        float originX = originPos[0];
        float originZ = originPos[1];

        for (int idx = 0; idx < sentence.length(); idx++) {
            char c = sentence.charAt(idx);
            float charHight = (float) charBitmaps[c].getHeight() / (float) HelloArActivity.height * scaleFactor;
            float charWidth = (float) charBitmaps[c].getWidth() / (float) HelloArActivity.width * scaleFactor;
            float bearingX = (float) charBitmaps[c].getBitmap_top() / (float) HelloArActivity.width * scaleFactor;
            float bearingZ = (float) charBitmaps[c].getBitmap_left() / (float) HelloArActivity.width * scaleFactor;
            float advance = (float) charBitmaps[c].getAdvance() / (float) HelloArActivity.width * scaleFactor;

            float startX = originX + bearingX;
            float startZ = originZ + bearingZ;
            float[] vertices = {
                    // x, y, z    // texture coordinates
                    startX, startZ, 0.0f, 0.0f, 0.0f,  // from the left-top corner
                    startX, startZ - charHight, 0.0f, 0.0f, 1.0f,
                    startX + charWidth, startZ, 0.0f, 1.0f, 0.0f,
                    startX + charWidth, startZ, 0.0f, 1.0f, 0.0f,
                    startX, startZ - charHight, 0.0f, 0.0f, 1.0f,
                    startX + charWidth, startZ - charHight, 0.0f, 1.0f, 1.0f

            };

            // bind texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, charBitmaps[c].getTextureId());

            // bind vertices
            FloatBuffer verticesBuffer = ByteBuffer.allocateDirect(vertices.length * BYTE_PER_FLOAT).order(ByteOrder.nativeOrder()).asFloatBuffer();
            verticesBuffer.put(vertices).position(0);
            GLES20.glVertexAttribPointer(positionAttribute, VERTEX_COORD_NUM, GLES20.GL_FLOAT, false, 5 * BYTE_PER_FLOAT, verticesBuffer);
            GLES20.glEnableVertexAttribArray(positionAttribute);
            verticesBuffer.position(VERTEX_COORD_NUM);
            GLES20.glVertexAttribPointer(texCoordAttribute, TEXTCOORD_NUM, GLES20.GL_FLOAT, false, 5 * BYTE_PER_FLOAT, verticesBuffer);
            GLES20.glEnableVertexAttribArray(texCoordAttribute);

            // set blend mode

            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

            originX += advance;
        }
    }
}
