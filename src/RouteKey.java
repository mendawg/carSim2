public class RouteKey {
    private final long startId;
    private final long endId;

    public RouteKey(long startId, long endId) {
        this.startId = startId;
        this.endId = endId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(startId) * 31 + Long.hashCode(endId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RouteKey r = (RouteKey) obj;
        return startId == r.startId && endId == r.endId;
    }
}