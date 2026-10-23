import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {

    // Mnożnik skalowania: 1 instancja Car = 10 rzeczywistych samochodów
    public static final int CAR_SCALE = 10;

    // Rzeczywiste cele (zostaną podzielone przez CAR_SCALE w getCarsCount)
    private static final int[] TARGETS = {
            1000, 700, 500, 500, 800, 2200, 6000, 10000, 9000, 6500, 5500, 5500, 6000, 7000, 8000, 11000, 10000,
            8000, 6000, 5000, 4000, 3000, 2000, 1000
    };

    private static int getCarsCount(int absTime) {
        int secondsInDay = absTime % 86400;
        int currHour = (secondsInDay / 3600) % TARGETS.length;
        int nextHour = (currHour + 1) % TARGETS.length;

        double fractionHour = (secondsInDay % 3600) / 3600.0;
        // POPRAWKA: Interpolacja między currHour a nextHour (wcześniej było TARGETS[idx] - TARGETS[idx])
        double target = TARGETS[currHour] + fractionHour * (TARGETS[nextHour] - TARGETS[currHour]);

        // Skalujemy liczbę instancji 10-krotnie w dół
        return (int) Math.round(target / CAR_SCALE);
    }

    public static final LinkedBlockingQueue<Record> violations = new LinkedBlockingQueue<>();
    public static final AtomicBoolean isRunning = new AtomicBoolean(true);
    public static final ConcurrentMap<RouteKey, List<Way>> routeCache = new ConcurrentHashMap<>(16384);

    private static double currentSimulatedTarget = 0;

    public static void main(String[] args) {
        try {
            // Szybkość przejścia również przeskalowana odpowiednio do mniejszej liczby obiektów
            double transitionSpeed = 2.0 / CAR_SCALE;

            String skrzyzowania = "intersections.csv";
            String drogi = "road_segments.csv";

            // POPRAWKA: Usunięto redundantne wywołania loadIntersections i loadWays,
            // ponieważ linkWays i tak ładuje je od nowa.
            System.out.println("Ładowanie grafu dróg...");
            List<Intersection> graph = DataLoader.linkWays(skrzyzowania, drogi);
            System.out.println("Załadowano skrzyżowań: " + graph.size());

            Weather weather = new Weather(graph);
            AStar pathFinder = new AStar();
            int dt = 1;
            List<Car> cars = new ArrayList<>(1500);

            long simulationSteps = 3600 * 24 * 1;

            System.out.println("Uruchamianie asynchronicznego zapisywania I/O...");
            Thread writer = new Thread(() -> {
                // Zwiększony bufor zapisu (64 KB) dla wyższej wydajności przy 10x większej liczbie logów na fotoradar
                try (BufferedWriter reportWriter = new BufferedWriter(new FileWriter("violations.csv"), 65536)) {
                    // POPRAWKA: Dodano brakującą kolumnę 'density' w nagłówku CSV
                    reportWriter.write("time,speed,limit,curvature,true_length,way_type,smoothness," +
                            "surface,is_one_way,is_lit,is_primary,lanes_no,way_id,weather,density\n");

                    List<Record> batch = new ArrayList<>(256);
                    while (isRunning.get() || !violations.isEmpty()) {
                        Record first = violations.poll(100, TimeUnit.MILLISECONDS);
                        if (first != null) {
                            batch.add(first);
                            violations.drainTo(batch, 255); // Pobieramy paczkę rekordów naraz, by zredukować blokady
                            for (Record record : batch) {
                                reportWriter.write(String.format(Locale.US,
                                        "%.1f,%.2f,%.1f,%.4f,%.2f,%s,%s,%s,%b,%b,%b,%d,%d,%s,%.4f\n",
                                        record.time,
                                        record.speed,
                                        record.limit,
                                        record.curvature,
                                        record.trueLength,
                                        record.wayType,
                                        record.smoothness,
                                        record.surface,
                                        record.isOneWay,
                                        record.isLit,
                                        record.isPrimary,
                                        record.lanesNo,
                                        record.wayId,
                                        record.weather,
                                        record.density
                                ));
                            }
                            batch.clear();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            writer.start();

            System.out.println("Rozpoczynam symulację...");

            long realStartTime = System.currentTimeMillis();
            long totalPhase1Time = 0;
            long totalPhase2Time = 0;

            for (int step = 0; step < simulationSteps; step++) {
                int absoluteTime = step * dt;
                long phase1Start = System.nanoTime();
                weather.checkEvents(absoluteTime);

                if (absoluteTime % 3600 == 0 && absoluteTime > 0) {
                    int hour = (absoluteTime % 86400) / 3600;
                    if (hour == 5) {
                        System.out.println(">>> ZMIANA POGODY O 5:00 <<<");
                        weather.changeWeather(absoluteTime);
                    }
                }

                cars.removeIf(Car::getArrived);

                int realTarget = getCarsCount(absoluteTime);

                if (step == 0) {
                    currentSimulatedTarget = realTarget;
                    System.out.println("Inicjalizacja: Trwa wyznaczanie tras (A*) dla początkowych "
                            + realTarget + " grup aut (odpowiada " + (realTarget * CAR_SCALE) + " autom). Proszę czekać...");
                }

                if (currentSimulatedTarget < realTarget) {
                    currentSimulatedTarget = Math.min(realTarget, currentSimulatedTarget + transitionSpeed);
                } else if (currentSimulatedTarget > realTarget) {
                    currentSimulatedTarget = Math.max(realTarget, currentSimulatedTarget - transitionSpeed);
                }

                int targetForThisStep = (int) Math.round(currentSimulatedTarget);
                int carsNeeded = targetForThisStep - cars.size();

                if (carsNeeded > 0) {
                    List<Car> newCars = IntStream.range(0, carsNeeded).parallel()
                            .mapToObj(i -> new Car(graph, pathFinder, dt, violations, routeCache))
                            .toList();
                    cars.addAll(newCars);
                }
                long phase1End = System.nanoTime();
                totalPhase1Time += (phase1End - phase1Start);

                long phase2Start = System.nanoTime();
                cars.parallelStream().forEach(car -> car.move(absoluteTime));

                if (absoluteTime % 60 == 0) {
                    int h = (absoluteTime % 86400) / 3600;
                    int m = (absoluteTime % 3600) / 60;

                    System.out.printf("[Czas: %02d:%02d] Instancje aut: %d / Cel: %d (Rzeczywiste: %d / %d)\n",
                            h, m, cars.size(), realTarget, cars.size() * CAR_SCALE, realTarget * CAR_SCALE);
                }
                long phase2End = System.nanoTime();
                totalPhase2Time += (phase2End - phase2Start);
            }

            isRunning.set(false);
            writer.join();

            double p1Seconds = totalPhase1Time / 1_000_000_000.0;
            double p2Seconds = totalPhase2Time / 1_000_000_000.0;
            System.out.printf("Czas spędzony w Fazie 1 (Tworzenie aut/Pogoda): %.2f s\n", p1Seconds);
            System.out.printf("Czas spędzony w Fazie 2 (Fizyka - wielowątkowo): %.2f s\n", p2Seconds);
            System.out.println("Cached routes: " + routeCache.size());
            long realEndTime = System.currentTimeMillis();
            System.out.printf("\nSymulacja zakończona! Obliczenie %d kroków zajęło %.2f sekund w czasie rzeczywistym.\n",
                    simulationSteps, (realEndTime - realStartTime) / 1000.0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class Record {
        public final double time;
        public final double speed;
        public final double limit;
        public final double curvature;
        public final double trueLength;
        public final double density;

        public final String wayType;
        public final String smoothness;
        public final String surface;

        public final boolean isOneWay;
        public final boolean isLit;
        public final boolean isPrimary;

        public final int lanesNo;
        public final int wayId;

        public final Weather.weatherType weather;

        public Record(double time, double speed, double limit, double curvature, double trueLength, String wayType,
                      String smoothness, String surface, boolean isOneWay, boolean isLit,
                      boolean isPrimary, int lanesNo, int wayId, Weather.weatherType weather, double density) {
            this.time = time;
            this.speed = speed;
            this.limit = limit;
            this.curvature = curvature;
            this.trueLength = trueLength;
            this.wayType = wayType;
            this.smoothness = smoothness;
            this.surface = surface;
            this.isOneWay = isOneWay;
            this.isLit = isLit;
            this.isPrimary = isPrimary;
            this.lanesNo = lanesNo;
            this.wayId = wayId;
            this.weather = weather;
            this.density = density;
        }
    }
}