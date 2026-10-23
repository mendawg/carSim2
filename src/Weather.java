import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Weather {

    private final Set<Way> affectedWays = new HashSet<>();
    private final List<WeatherEvent> activeEvents = new ArrayList<>();
    private final int monthS = 2592000;
    private final List<Intersection> intersections;

    public Weather(List<Intersection> i) {
        this.intersections = i;
    }

    private int currentMonth(int absTime) {
        return (absTime / monthS) % 12 + 1;
    }

    protected enum weatherType {
        SUNNY,
        RAINY,
        SNOWY
    }

    private enum season {
        SPRING,
        SUMMER,
        FALL,
        WINTER
    }

    private season currentSeason(int absTime) {
        int month = currentMonth(absTime);
        return switch (month) {
            case 12, 1, 2 -> season.WINTER;
            case 3, 4, 5 -> season.SPRING;
            case 6, 7, 8 -> season.SUMMER;
            case 9, 10, 11 -> season.FALL;
            default -> throw new IllegalArgumentException();
        };
    }

    private weatherType chooseDuringWinter() {
        double dice = ThreadLocalRandom.current().nextDouble();
        if (dice < 0.15) return weatherType.SUNNY;
        else if (dice < 0.2) return weatherType.RAINY;
        else return weatherType.SNOWY;
    }

    private weatherType chooseDuringSummer() {
        return ThreadLocalRandom.current().nextDouble() < 0.85 ? weatherType.SUNNY : weatherType.RAINY;
    }

    private weatherType chooseDuringFall() {
        return ThreadLocalRandom.current().nextDouble() < 0.65 ? weatherType.SUNNY : weatherType.RAINY;
    }

    private weatherType chooseDuringSpring() {
        return ThreadLocalRandom.current().nextDouble() < 0.70 ? weatherType.SUNNY : weatherType.RAINY;
    }

    private weatherType chooseWeather(int absTime) {
        return switch (currentSeason(absTime)) {
            case SPRING -> chooseDuringSpring();
            case SUMMER -> chooseDuringSummer();
            case FALL -> chooseDuringFall();
            case WINTER -> chooseDuringWinter();
        };
    }

    public void changeWeather(int absTime) {
        if (intersections.isEmpty()) return;
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int fraction = rand.nextInt(Math.max(1, intersections.size() / 100));

        if (fraction == 0) return;

        int size = intersections.size();
        for (int k = 0; k < fraction; k++) {
            Intersection i = intersections.get(rand.nextInt(size));
            int duration = rand.nextInt(1200, 54000);

            Set<Way> affected = new HashSet<>(i.getWaysDeep());
            affected.removeAll(affectedWays);
            if (affected.isEmpty()) continue;

            weatherType newWeather = chooseWeather(absTime);
            WeatherEvent event = new WeatherEvent(i, newWeather, absTime + duration, affected);
            activeEvents.add(event);
            affectedWays.addAll(affected);

            for (Way w : affected) {
                w.setCurrentWeather(newWeather);
            }
        }
    }

    public void checkEvents(int absTime) {
        Iterator<WeatherEvent> iter = activeEvents.iterator();
        while (iter.hasNext()) {
            WeatherEvent event = iter.next();
            if (event.getEndTime() <= absTime) {
                for (Way w : event.getAffected()) {
                    w.setCurrentWeather(weatherType.SUNNY);
                }
                affectedWays.removeAll(event.getAffected());
                iter.remove();
            }
        }
    }
}