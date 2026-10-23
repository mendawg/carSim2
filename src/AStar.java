import java.util.*;

public class AStar {

    private static class WeightedIntersection implements Comparable<WeightedIntersection> {
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
        final Intersection previous;
        final Way wayTaken;

        public Step(Intersection previous, Way wayTaken) {
            this.previous = previous;
            this.wayTaken = wayTaken;
        }
    }

    public List<Way> findPath(Intersection start, Intersection destination) {
        if (start.equals(destination)) {
            return Collections.emptyList();
        }

        PriorityQueue<WeightedIntersection> openSet = new PriorityQueue<>(64);
        Map<Intersection, Double> gScores = new HashMap<>(128);
        Map<Intersection, Step> cameFrom = new HashMap<>(128);
        Set<Intersection> closedSet = new HashSet<>(128);

        gScores.put(start, 0.0);
        openSet.add(new WeightedIntersection(start, quickDistance(start, destination)));

        while (!openSet.isEmpty()) {
            Intersection current = openSet.poll().intersection;

            if (!closedSet.add(current)) continue; // Zwraca false jeśli już był w zbiorze (1 operacja zamiast 2)

            if (current.equals(destination)) {
                return Collections.unmodifiableList(reconstruct(cameFrom, current));
            }

            double currG = gScores.get(current);

            for (Way way : current.getOutgoingWays()) {
                Intersection neighbour = way.getFinalNode();
                if (neighbour.equals(current)) {
                    neighbour = way.getStartNode();
                }
                if (closedSet.contains(neighbour)) continue;

                double penalty = penalize(way);
                double neighbourCurrentG = currG + (way.getTrueLength() * penalty);
                double neighbourG = gScores.getOrDefault(neighbour, Double.MAX_VALUE);

                if (neighbourCurrentG < neighbourG) {
                    cameFrom.put(neighbour, new Step(current, way));
                    gScores.put(neighbour, neighbourCurrentG);
                    double weight = 1.5;
                    double fScore = neighbourCurrentG + (weight * quickDistance(neighbour, destination));
                    openSet.add(new WeightedIntersection(neighbour, fScore));
                }
            }
        }
        return null;
    }

    private double quickDistance(Intersection x, Intersection y) {
        final double R = 6371000.0;

        double latX = Math.toRadians(x.getLat());
        double latY = Math.toRadians(y.getLat());

        double dLat = latX - latY;
        double dLon = Math.toRadians(x.getLon() - y.getLon());

        double meanLat = (latX + latY) * 0.5;

        double xDist = dLon * Math.cos(meanLat);
        return R * Math.sqrt(xDist * xDist + dLat * dLat);
    }

    private List<Way> reconstruct(Map<Intersection, Step> cameFrom, Intersection current) {
        LinkedList<Way> path = new LinkedList<>();
        while (cameFrom.containsKey(current)) {
            Step step = cameFrom.get(current);
            path.addFirst(step.wayTaken);
            current = step.previous;
        }
        return path;
    }

    private double penalize(Way way) {
        double maxLimit = 140.0 / 3.6; // m /s
        double limit = (way.getSpeedLimit() > 0) ? way.getSpeedLimit() : (50.0 / 3.6);
        double penalty = maxLimit / limit;

        String wayType = way.getWayType() != null ? way.getWayType() : "unknown";

        if (wayType.equals("residential") || wayType.equals("construction")) penalty *= 3.0;
        else if (wayType.equals("tertiary") || wayType.equals("unclassified")) penalty *= 1.5;

        if (way.getLanesNo() > 1) penalty *= 0.8;

        double densityLimit = (way.getTrueLength() / 1000.0) * way.getLanesNo() * 133.0;
        double currentLoad = way.getWayDensity() * way.getTrueLength() * way.getLanesNo();

        if (currentLoad > 0 && densityLimit > 0) {
            double occupancyRatio = currentLoad / densityLimit;
            if (occupancyRatio >= 1.0) {
                penalty *= 50.0;
            } else {
                penalty *= (1.0 + (occupancyRatio * occupancyRatio * occupancyRatio) * 10.0);
            }
        }

        return Math.max(1.0, penalty);
    }
}