import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SimulationUseCaseTest {

    public static void main(String[] args) {
        try {
            // 1. Przygotowanie danych testowych
            File intersFile = createTestIntersectionsCsv();
            File waysFile = createTestWaysCsv();

            System.out.println("Ładowanie grafu dróg...");
            // DODANO: trzeci argument 'false' (assumeBi)
            List<Intersection> graph = DataLoader.linkWays(
                    intersFile.getAbsolutePath(),
                    waysFile.getAbsolutePath()
            );
            System.out.println("Załadowano skrzyżowań: " + graph.size());

            // 3. Inicjalizacja infrastruktury symulacyjnej
            AStar pathFinder = new AStar();
            double dt = 0.5; // Krok symulacji: pół sekundy
            List<Car> cars = new ArrayList<>();

            // Tworzymy 5 samochodów
//            for (int i = 0; i < 5; i++) {
//                cars.add(new Car(graph, pathFinder, dt, ));
//            }
            System.out.println("Utworzono samochodów: " + cars.size());

            // 4. Pętla symulacji (Wydłużona do 120 sekund, aby złapać pełen cykl świateł)
            System.out.println("Rozpoczynam symulację...");
            int simulationSteps = (int) (120.0 / dt);

            for (int step = 0; step < simulationSteps; step++) {
                double absoluteTime = step * dt;

                for (Car car : cars) {
                    car.move(absoluteTime);
                }

                // Diagnostyka co 5 sekund czasu symulacji (10 kroków * 0.5s)
                if (step % 10 == 0) {
                    System.out.printf("\n--- Sekunda symulacji: %.1f s ---\n", absoluteTime);
                    for (int j = 0; j < cars.size(); j++) {
                        // Dodaj w klasie Car metodę boolean isArrived() { return arrived; }
                        // aby nie wypisywać aut, które już skończyły podróż.
                        if (!cars.get(j).getArrived()) {
                            System.out.print("Samochód " + j + " -> ");
                            cars.get(j).printDiagnostics();
                        }
                    }
                }
            }

            // 5. Weryfikacja
            System.out.println("\n--- WYNIKI SYMULACJI ---");
            for (int i = 0; i < cars.size(); i++) {
                Car car = cars.get(i);
                System.out.println("Samochód " + i + ":");
                car.printDiagnostics();
            }

        } catch (Exception e) {
            System.err.println("Symulacja zakończyła się awarią:");
            e.printStackTrace();
        }
    }

    // --- ZMODYFIKOWANE METODY GENERUJĄCE GRAF Z KOLIZJĄ ---

    private static File createTestIntersectionsCsv() throws IOException {
        File file = File.createTempFile("test_intersections", ".csv");
        file.deleteOnExit();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("id,lat,lon\n");
            writer.write("1,52.2297,21.0122\n"); // Skrzyżowanie A
            writer.write("2,52.2300,21.0130\n"); // Skrzyżowanie B (Węzeł centralny)
            writer.write("3,52.2310,21.0150\n"); // Skrzyżowanie C
            writer.write("4,52.2290,21.0130\n"); // Skrzyżowanie D (Nowy wlot do B)
        }
        return file;
    }

    private static File createTestWaysCsv() throws IOException {
        File file = File.createTempFile("test_ways", ".csv");
        file.deleteOnExit();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("wayId,speedLimit,surface,trueLength,lengthLine,isOneWay,isLit,wayType,lanesNo,smoothness,startNodeId,finalNodeId\n");
            // Droga A -> B (Wlot 1 na skrzyżowanie B)
            writer.write("101,50,asphalt,200.0,200.0,yes,yes,primary,1,good,1,2\n");
            // Droga B -> C (Wylot ze skrzyżowania B)
            writer.write("102,90,asphalt,500.0,490.0,yes,yes,highway,2,good,2,3\n");
            // Droga D -> B (Wlot 2 na skrzyżowanie B - tworzy kolizję świateł!)
            writer.write("103,50,asphalt,200.0,200.0,yes,yes,primary,1,good,4,2\n");
        }
        return file;
    }
}