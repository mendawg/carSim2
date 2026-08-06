import java.util.*;

public class AStar {

    private static class WeightedIntersection implements Comparable<WeightedIntersection> {
//        Nadajemy skrzyzowaniom wagi, by moc wrzucic je do PriorityQueue

        final Intersection intersection;
        final double fCost;

        public WeightedIntersection(Intersection intersection, double fCost) {
            this.intersection = intersection;
            this.fCost = fCost;
        }

        @Override
        public int compareTo(WeightedIntersection other) {
            return Double.compare(this.fCost, other.fCost);
        }
    }

    private static class Step {
//      Przechowujemy informacje o poprzednim kroku, by potem moc odtworzyc sciezke
        final Intersection previous;
        final Way wayTaken;

        public Step(Intersection previous, Way wayTaken) {
            this.previous = previous;
            this.wayTaken = wayTaken;
        }
    }

    public  List<Way> findPath(Intersection start, Intersection destination) {
//      Jesli odrazu jestesmy u celu to zakoncz
        if (start.equals(destination)) {
            return Collections.emptyList();
        }

        PriorityQueue<WeightedIntersection> openSet = new PriorityQueue<>(); //Najbardziej obiecujace skrzyzowania
        Map<Intersection, Double> gScores = new HashMap<>(); // Wyniki G kazdego skrzyzowania
        Map<Intersection, Step> cameFrom = new HashMap<>();
        Set<Intersection> closedSet = new HashSet<>(); //odwiedzone wezly
        gScores.put(start, 0.0);

        openSet.add(new WeightedIntersection(start, quickDistance(start,destination)));

        while(!openSet.isEmpty()){
            Intersection current = openSet.poll().intersection; //pobieramy najbardziej obiecujacego kandydata

            if (closedSet.contains(current)) continue; // jesli juz odwiedzony to nas nie obchodzi
            closedSet.add(current);

            if (current.equals(destination)){ //jesli jest on celem to konczymy dzialanie
                return Collections.unmodifiableList(reconstruct(cameFrom, current)); // lista nie moze byc modyfikowana
                // jesli ma byc odczytywana przez wiele watkow
            }

            double currG = gScores.get(current); //jesli nie to pobieramy jego G

            for(Way way : current.getOutgoingWays()){
                Intersection neighbour = way.getFinalNode();
                if (neighbour.equals(current)) {
                    neighbour = way.getStartNode();
                }
                if (closedSet.contains(neighbour)) continue;
//              Sprawdzamy kazde sasiednie skrzyzowanie
                double penalty = penalize(way);
                double neighbourCurentG = currG + (way.getTrueLength() * penalty); // Dla kazdego z nich obliczamy G
                double neighbourG = gScores.getOrDefault(neighbour, Double.MAX_VALUE); // Jesli skrzyzowanie mialo juz wyliczone G to je pobieramy
                                                                                       // Jesli nie - ustalamy ze jego G jest maksymalne
                if (neighbourCurentG < neighbourG){
                    cameFrom.put(neighbour, new Step(current,way)); // Notujemy skad przyszlismy
                    gScores.put(neighbour,neighbourCurentG);
                    // Celowe dzialanie, A*. Tracimy gwarancje najkrotszej sciezki ale zaoszczedzamy czas na obliczaniu
                    double weight = 1.5;
                    double fScore = neighbourCurentG + (weight *quickDistance(neighbour, destination)) ; // Liczymy F score
                    openSet.add(new WeightedIntersection(neighbour, fScore));
                    // Wrzucamy skrzyzowanie na liste kandydatow. W nastepnej iteracji algorytm sam je wybierze jesli jest obiecujace
                }

            }
        }
        return null; // jesli nie udalo sie znalezc sciezki

    }
    private double haversineDistance(Intersection x, Intersection y) {
        final int R = 6371000;

        double latX = Math.toRadians(x.getLat());
        double latY = Math.toRadians(y.getLat());

        double dLat = latX - latY;
        double dLon = Math.toRadians(x.getLon() - y.getLon());

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(latX) * Math.cos(latY) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
//aproksymacja plaska
    private double quickDistance(Intersection x, Intersection y){
        final int R = 6371000;

        double latX = Math.toRadians(x.getLat());
        double latY = Math.toRadians(y.getLat());

        double dLat = latX - latY;
        double dLon = Math.toRadians(x.getLon() - y.getLon());

        double meanLat = (latX + latY) / 2.0;

        double xDist = dLon * Math.cos(meanLat);
        return R * Math.sqrt(xDist * xDist + dLat * dLat);
    }




    private List<Way> reconstruct(Map<Intersection, Step> cameFrom, Intersection current){
        LinkedList<Way> path = new LinkedList<>();
        while(cameFrom.containsKey(current)){
            Step step = cameFrom.get(current);
            path.addFirst(step.wayTaken);
            current = step.previous;
        }
        return path;
    }

    private double penalize(Way way){
//      Chcemy zniechecic samochody do jazdy drogami osiedlowymi
        double maxLimit = 140;
        double limit = (way.getSpeedLimit() != null && way.getSpeedLimit() > 0) ? way.getSpeedLimit() : 50.0;
        double penalty = maxLimit / limit;

        String wayType = way.getWayType() != null ?  way.getWayType() : "unknown";

        if (wayType.equals("residential") || wayType.equals("construction")) penalty *= 3.0;
        else if (wayType.equals("tertiary") || wayType.equals("unclassified")) penalty *= 1.5;

        if (way.getLanesNo() > 1) penalty *= 0.8;
        // chcemy tez zniechecic do wyboru zakorkowanych tras
//        zakladamy ze 1km pasa miesci 133 samochody
        double densityLimit = (way.getTrueLength() / 1000) * way.getLanesNo() * 133.0;
        double currentLoad = way.getWayDensity() * (way.getTrueLength() * way.getLanesNo() / 1000);

        if (currentLoad > 0) {
            double occupancyRatio = currentLoad / densityLimit;
            if (occupancyRatio >= 1.0) {
                penalty *= 50.0;// pelna droga
            } else {
                penalty *= (1.0 + Math.pow(occupancyRatio, 3) * 10.0);
            }
        }

        return Math.max(1.0,penalty);
    }
}
