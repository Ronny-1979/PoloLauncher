package de.ronny.pololauncher;
import android.graphics.Bitmap;
import android.content.Context;
public final class MediaInfoTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        Bitmap large=new Bitmap(1024,1024);
        MediaInfo.updateArtwork(large);Bitmap first=MediaInfo.artwork();
        for(int i=0;i<100;i++)MediaInfo.updateArtwork(large);
        check(Bitmap.scales==1 && first==MediaInfo.artwork(),"Same source not scaled repeatedly");
        check(first.getWidth()==256,"Artwork remains bounded");
        MediaInfo.updateArtwork(null);check(MediaInfo.artwork()==null,"Missing artwork clears previous logo");
        MediaInfo.updateArtwork(large);check(Bitmap.scales==2,"Artwork can be restored after clearing");
        Context context=new Context();
        MediaInfo.update(context,"Song","Artist",true);
        check(MediaInfo.title().equals("Song") && MediaInfo.artist().equals("Artist"),"Metadata retained");
        MediaInfo.update(context,"Next","DLS-",true);
        check(MediaInfo.artist().equals("Interpret nicht verfügbar"),"DLS placeholder not used as artist");
        MediaInfo.update(context,"Fun Factory - Don't Go Away","Don't Go Away","Fun Factory",true);
        check(!MediaInfo.station().equals("Fun Factory - Don't Go Away"),"Artist-title DLS line is not accepted as station");
        MediaInfo.update(context,"Sunshine live - 90er","Don't Go Away","Fun Factory",true);
        check(MediaInfo.station().equals("Sunshine live - 90er"),"Real station label is retained");
        String huge="X".repeat(5000);
        MediaInfo.update(context,"Station",huge,huge+"artist",true);
        check(MediaInfo.title().length()<=512 && MediaInfo.artist().length()<=512,
                "Hostile/buggy media metadata is bounded before persistence and UI use");
        System.out.println("MediaInfoTest: all cases passed");
    }
}
