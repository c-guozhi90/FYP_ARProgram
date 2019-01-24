package chenyue.arfyp.common.freetype;


public class FreetypeJNI {
    static {
        System.loadLibrary("myFont");
    }

    private byte[] bitmap;
    private int bitmap_left;
    private int bitmap_top;
    private int height;
    private int width;

    public static native FreetypeJNI[] getFontTexFromC();

    public FreetypeJNI(int bitmap_left, int bitmap_top, int height, int width, byte[] bitmap) {
        this.bitmap = bitmap;
        this.bitmap_left = bitmap_left;
        this.bitmap_top = bitmap_top;
        this.height = height;
        this.width = width;
    }

    public static void printChar() {
        FreetypeJNI[] FreetypeJNI = getFontTexFromC();
        for (int i = 0; i < FreetypeJNI.length; i++) {
            byte[] tempBuffer = FreetypeJNI[i].getBitmap();
            for (int row = 0; row < FreetypeJNI[i].getHeight(); row++) {
                for (int col = 0; col < FreetypeJNI[i].getWidth(); col++) {
                    if ((int) tempBuffer[row * FreetypeJNI[i].getWidth() + col] != 0) {
                        System.out.print("+");
                    } else {
                        System.out.print("-");
                    }
                }
                System.out.println();
            }
        }
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

}
