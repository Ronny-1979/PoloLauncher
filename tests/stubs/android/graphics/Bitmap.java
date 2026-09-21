package android.graphics;
public class Bitmap {
    public static int scales;
    private final int width,height;
    public Bitmap(int width,int height){this.width=width;this.height=height;}
    public int getWidth(){return width;} public int getHeight(){return height;}
    public static Bitmap createScaledBitmap(Bitmap source,int width,int height,boolean filter){scales++;return new Bitmap(width,height);}
}
