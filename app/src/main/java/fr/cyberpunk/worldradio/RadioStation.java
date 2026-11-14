package fr.cyberpunk.worldradio;

public class RadioStation {
    public final String name;
    public final String url;
    public final String country;
    public final String tags;
    public final String favicon;
    public final int bitrate;
    public final String codec;

    public RadioStation(String name, String url, String country, String tags,
                        String favicon, int bitrate, String codec) {
        this.name = name;
        this.url = url;
        this.country = country;
        this.tags = tags;
        this.favicon = favicon;
        this.bitrate = bitrate;
        this.codec = codec;
    }

    public String subtitle() {
        StringBuilder sb = new StringBuilder();
        if (country != null && !country.isEmpty()) sb.append(country);
        if (bitrate > 0) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append(bitrate).append(" kbps");
        }
        if (codec != null && !codec.isEmpty()) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append(codec);
        }
        return sb.toString();
    }
}
