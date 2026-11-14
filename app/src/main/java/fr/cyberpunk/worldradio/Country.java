package fr.cyberpunk.worldradio;

public class Country {
    public final String name;
    public final String code;
    public final int stationCount;

    public Country(String name, String code, int stationCount) {
        this.name = name;
        this.code = code;
        this.stationCount = stationCount;
    }

    @Override
    public String toString() {
        return stationCount > 0 ? name + " (" + stationCount + ")" : name;
    }
}
