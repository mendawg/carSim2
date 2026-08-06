import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class Testing {

    public static void main(String[] args) {
        String skrzyzowania = "/home/mendawg/moje/skrypty/carsim2/czesc_python/intersections.csv";
        String drogi = "/home/mendawg/moje/skrypty/carsim2/czesc_python/road_segments.csv";

        Map<Long, Intersection> x = DataLoader.loadIntersections(skrzyzowania);
        List<Way> y = DataLoader.loadWays(drogi);

        // 1. Ochrona przed uszkodzonymi danymi (Topological Integrity)
        for (Way way : y) {
            Intersection start = x.get(way.getStartNodeId());
            Intersection koniec = x.get(way.getFinalNodeId());

            if (start == null || koniec == null) {
                System.err.println("Niespójność grafu: Droga " + way.getWayId() + " wskazuje na nieistniejący węzeł. Pomijam.");
                continue;
            }

            way.setStartNode(start);
            way.setFinalNode(koniec);

            // Relacje wychodzące i wchodzące (wymagane m.in. do sygnalizacji świetlnej)
            start.addOutgoingWay(way);
            koniec.addIncomingWay(way);


            boolean isOneWay = way.getOneWay() != null ? way.getOneWay() : false;

            if (!isOneWay) {
                koniec.addOutgoingWay(way);
                start.addIncomingWay(way);
            }
        }

        System.out.println("=== DIAGNOSTYKA GRAFU ===");
        System.out.println("Załadowano dróg: " + y.size());
        System.out.println("Załadowano węzłów: " + x.size());

        // Obliczanie statystyk połączeń zamiast nieczytelnego potoku liczb
        int isolatedNodes = 0;
        for (Intersection i : x.values()) {
            if (i.getOutgoingWays().isEmpty()) {
                isolatedNodes++;
            }
        }
        System.out.println("Liczba odizolowanych węzłów (brak dróg wyjazdowych): " + isolatedNodes);
        System.out.println("=========================\n");

        List<Intersection> values = new ArrayList<>(x.values());
        AStar a = new AStar();
        for (Intersection i : x.values()){
            System.out.println(i.getOutgoingWays().size());
        }
        // 2. Deterministyczny warunek stopu dla wyszukiwania
        int maxAttempts = 1000;
        int attempts = 0;
        boolean pathFound = false;

        while (attempts < maxAttempts) {
            attempts++;

            Intersection start = values.get(ThreadLocalRandom.current().nextInt(values.size()));
            Intersection koniec = values.get(ThreadLocalRandom.current().nextInt(values.size()));

            if (start.equals(koniec)) {
                continue;
            }

            List<Way> sciezka = a.findPath(start, koniec);

            if (sciezka != null && !sciezka.isEmpty()) {
                System.out.println("Znaleziono ścieżkę po " + attempts + " próbach losowania.");
                System.out.println("Trasa przejazdu (ID dróg):");
                for (Way w : sciezka) {
                    System.out.println(" -> " + w.getWayId());
                }
                pathFound = true;
                break;
            }
        }

        if (!pathFound) {
            System.err.println("BŁĄD: Nie udało się znaleźć żadnej poprawnej ścieżki w grafie po " + maxAttempts + " próbach. Graf może być silnie niespójny.");
        }
    }
}