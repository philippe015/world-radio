package fr.cyberpunk.worldradio;

/** État audio et métadonnées de la station, partagés entre les écrans. */
public final class AudioState {
    public static volatile int sessionId = 0;
    public static volatile String currentStation = "";

    public static volatile String stationName = "";
    public static volatile String stationCountry = "";
    public static volatile String stationFavicon = "";
    public static volatile int stationBitrate = 0;

    private AudioState() {}
}
