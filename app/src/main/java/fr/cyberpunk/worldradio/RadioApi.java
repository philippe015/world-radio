package fr.cyberpunk.worldradio;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Client de l'API publique Radio Browser (aucune clé requise). */
public final class RadioApi {

    private static final String BASE = "https://de1.api.radio-browser.info";
    private static final String UA = "WorldRadio/1.0 (Android)";

    private RadioApi() {}

    /** Recherche combinée : texte libre + pays + style (tag). */
    public static List<RadioStation> search(String query, String countryCode, String tag)
            throws Exception {
        boolean noFilter = isEmpty(query) && isEmpty(countryCode) && isEmpty(tag);
        if (noFilter) {
            return parse(get(BASE + "/json/stations/topclick/60?hidebroken=true"));
        }
        StringBuilder url = new StringBuilder(BASE)
                .append("/json/stations/search?limit=60&order=clickcount&reverse=true&hidebroken=true");
        if (!isEmpty(query)) url.append("&name=").append(URLEncoder.encode(query.trim(), "UTF-8"));
        if (!isEmpty(countryCode)) url.append("&countrycode=").append(URLEncoder.encode(countryCode, "UTF-8"));
        if (!isEmpty(tag)) url.append("&tagList=").append(URLEncoder.encode(tag.trim(), "UTF-8"));
        return parse(get(url.toString()));
    }

    /** Liste des pays disponibles, triés par nom. */
    public static List<Country> countries() throws Exception {
        JSONArray arr = new JSONArray(get(BASE + "/json/countries"));
        List<Country> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String name = o.optString("name", "").trim();
            String code = o.optString("iso_3166_1", "").trim();
            int count = o.optInt("stationcount", 0);
            if (name.isEmpty() || code.isEmpty() || count < 10) continue;
            out.add(new Country(name, code, count));
        }
        Collections.sort(out, Comparator.comparing(c -> c.name));
        return out;
    }

    /** Styles (tags) les plus représentés dans le monde. */
    public static List<String> tags() throws Exception {
        JSONArray arr = new JSONArray(get(
                BASE + "/json/tags?order=stationcount&reverse=true&limit=90"));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String name = o.optString("name", "").trim();
            int count = o.optInt("stationcount", 0);
            if (name.isEmpty() || count < 30) continue;
            out.add(name);
        }
        return out;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        InputStream is = (code >= 400) ? c.getErrorStream() : c.getInputStream();
        String body = readAll(is);
        if (code >= 400) throw new Exception("Serveur Radio Browser : HTTP " + code);
        return body;
    }

    private static List<RadioStation> parse(String json) throws Exception {
        JSONArray arr = new JSONArray(json);
        List<RadioStation> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String name = o.optString("name", "").trim();
            String url = o.optString("url_resolved", "");
            if (url.isEmpty()) url = o.optString("url", "");
            if (name.isEmpty() || url.isEmpty()) continue;
            out.add(new RadioStation(
                    name,
                    url,
                    o.optString("country", ""),
                    o.optString("tags", ""),
                    o.optString("favicon", ""),
                    o.optInt("bitrate", 0),
                    o.optString("codec", "")));
        }
        return out;
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "[]";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toString("UTF-8");
    }
}
