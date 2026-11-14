package fr.cyberpunk.worldradio;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Chargeur d'images minimal (logos), sans dépendance externe. */
public final class ImageLoader {

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(120);
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private ImageLoader() {}

    public static void load(final String url, final ImageView view) {
        if (url == null || url.isEmpty()) { view.setImageDrawable(null); return; }
        final String key = url;
        Bitmap cached = CACHE.get(key);
        if (cached != null) { view.setImageBitmap(cached); return; }
        view.setImageDrawable(null);
        view.setTag(key);
        POOL.execute(() -> {
            Bitmap bmp = fetch(url);
            if (bmp != null) CACHE.put(key, bmp);
            UI.post(() -> {
                if (key.equals(view.getTag()) && bmp != null) view.setImageBitmap(bmp);
            });
        });
    }

    private static Bitmap fetch(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "WorldRadio/1.0");
            c.setInstanceFollowRedirects(true);
            InputStream is = c.getInputStream();
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            return BitmapFactory.decodeStream(is, null, opts);
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
