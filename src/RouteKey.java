import java.util.Objects;

public class RouteKey {
    private long startId;
    private long endId;


    public RouteKey(long startId, long endId) {
        this.startId = startId;
        this.endId = endId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startId,endId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RouteKey r = (RouteKey) obj;
        return startId == r.startId && endId == r.endId;
    }
}
