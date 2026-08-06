import java.util.Set;

class WeatherEvent {

    private final Intersection epicenter;
    private final Weather.weatherType type;
    private final int endTime;
    private final Set<Way> affected;

    public WeatherEvent(Intersection epicenter, Weather.weatherType type, int endTime, Set<Way> affected) {
        this.epicenter = epicenter;
        this.type = type;
        this.endTime = endTime;
        this.affected = affected;
    }

    public int getEndTime() {
        return endTime;
    }

    public Weather.weatherType getType() {
        return type;
    }

    public Set<Way> getAffected() {
        return affected;
    }
}