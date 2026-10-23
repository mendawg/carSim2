import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Intersection {

    private final double lat;
    private final double lon;
    private final long id;

    private final List<Way> incomingWays = new ArrayList<>();
    private final List<Way> outgoingWays = new ArrayList<>();

    public Intersection(Long id, Double lat, Double lon) {
        this.id = id;
        this.lat = lat;
        this.lon = lon;
    }

    public boolean hasGreenLight(Way way, double absTime) {
        int size = incomingWays.size();
        if (size <= 1) return true;

        double greenDuration = 60.0;
        int currentCycle = (int) (absTime / greenDuration);
        int hasGreenIndex = currentCycle % size;
        return incomingWays.get(hasGreenIndex) == way || incomingWays.get(hasGreenIndex).equals(way);
    }

    public void addIncomingWay(Way way) {
        incomingWays.add(way);
    }

    public void addOutgoingWay(Way way) {
        outgoingWays.add(way);
    }

    public List<Way> getIncomingWays() {
        return incomingWays;
    }

    public List<Way> getOutgoingWays() {
        return outgoingWays;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public long getId() {
        return id;
    }

    public Set<Way> getWaysDeep() {
        Set<Way> ways = new HashSet<>(incomingWays);
        ways.addAll(outgoingWays);
        Set<Way> allWays = new HashSet<>(ways);
        for (Way w : ways) {
            Intersection node = w.getFinalNode();
            if (node != null) {
                allWays.addAll(node.getIncomingWays());
                allWays.addAll(node.getOutgoingWays());
            }
        }
        return allWays;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Intersection that = (Intersection) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}