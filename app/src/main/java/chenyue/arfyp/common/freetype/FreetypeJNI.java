package chenyue.arfyp.common.freetype;


import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FreetypeJNI {
    static {
        System.loadLibrary("readFont");
    }

    private int textureId;
    private byte[] bitmap;
    private int bitmap_left;
    private int bitmap_top;
    private int height;
    private int width;
    private int advance;
    private static String fontPath;
    private static final String TAG = "FreetypeJNI";

    public static native FreetypeJNI[] getFontTexFromC(String path);

    public FreetypeJNI(int bitmap_left, int bitmap_top, int height, int width, int advance, byte[] bitmap) {
        this.bitmap = bitmap;
        this.bitmap_left = bitmap_left;
        this.bitmap_top = bitmap_top;
        this.height = height;
        this.width = width;
        this.advance = advance;
    }

    public FreetypeJNI() {
    }

    /**
     * Extract font to private data folder. This step is essential because files in assets are packed into apk
     */
    public static void extractFontFromAsset(Context context) throws IOException {
        File fontFile = context.getDir("fonts", Context.MODE_PRIVATE);
        AssetManager assetsManager = context.getAssets();
        try {
            if (fontFile.listFiles().length != 0)
                throw new Exception("font file exist");  // skip copying the font file
            InputStream is = assetsManager.open("font/Ubuntu-B.ttf");
            Log.d(TAG, "the absolute directory will be " + fontFile.getAbsolutePath());
            FileOutputStream fos = new FileOutputStream(fontFile.getAbsolutePath() + "Ubuntu-B.ttf");
            fontPath = fontFile.getAbsolutePath();
            byte[] buffer = new byte[512];
            int num;
            while ((num = is.read(buffer, 0, 512)) > 0) {
                fos.write(buffer, 0, num);
                fos.flush();
            }
            fos.close();
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    /*
     * initialize the all ascii charBitmaps
     * */
    public static FreetypeJNI[] initCharBitmaps(Context context) throws IOException {
        extractFontFromAsset(context);
        FreetypeJNI charBitmaps[] = getFontTexFromC(fontPath + "Ubuntu-B.ttf");
        return charBitmaps;
    }

    public byte[] getBitmap() {
        return bitmap;
    }

    public void setBitmap(byte[] bitmap) {
        this.bitmap = bitmap;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getBitmap_left() {
        return bitmap_left;
    }

    public void setBitmap_left(int bitmap_left) {
        this.bitmap_left = bitmap_left;
    }

    public int getBitmap_top() {
        return bitmap_top;
    }

    public void setBitmap_top(int bitmap_top) {
        this.bitmap_top = bitmap_top;
    }

    public int getTextureId() {
        return textureId;
    }

    public void setTextureId(int textureId) {
        this.textureId = textureId;
    }

    public int getAdvance() {
        return advance;
    }

    public void setAdvance(int advance) {
        this.advance = advance;
    }
}
