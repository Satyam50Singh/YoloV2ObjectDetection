package satya.app.objectdetectionyolov2.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class Utils {
    public static Bitmap getBitmapFromUri(Uri uri, Context context) {
        Bitmap selectedBitmap = null;
        final InputStream imageStream;
        try {
            imageStream = context.getContentResolver().openInputStream(uri);
            selectedBitmap = BitmapFactory.decodeStream(imageStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return selectedBitmap;
    }
}
