import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Way {

    private final boolean hasCamera;
    private final boolean isOneWay;
    private final boolean isLit;
    private final boolean isPrimary;

    private final double speedLimit;
    private final int wayId;
    private final int lanesNo;

    // Licznik przechowuje teraz rzeczywistą (przeskalowaną) liczbę aut na drodze
    private final AtomicInteger activeCars = new AtomicInteger(0);

    private final double lengthLine;
    private double trueLength;
    private final double cameraLocation;
    private final double curvature;
    private volatile double wayDensity = 0;

    private final String surface;
    private final String wayType;
    private final String smoothness;

    private Intersection startNode;
    private Intersection finalNode;

    private final long startNodeId;
    private final long finalNodeId;

    private Camera camera;

    private volatile Weather.weatherType currentWeather = Weather.weatherType.SUNNY;

    private static boolean randomHasCamera() {
        return ThreadLocalRandom.current().nextDouble() < 0.10;
    }

    private double randomCameraDistance() {
        return ThreadLocalRandom.current().nextDouble() * this.trueLength;
    }

    public Way(boolean isOneWay, boolean isLit, int speedLimit, int wayId, int lanesNo,
               double lengthLine, double trueLength, String surface, String wayType, String smoothness,
               Long startNodeId, Long finalNodeId) {

        this.isOneWay = isOneWay;
        this.isLit = isLit;
        this.speedLimit = speedLimit / 3.6;
        this.wayId = wayId;
        this.lanesNo = Math.max(1, lanesNo);
        this.isPrimary = "primary".equalsIgnoreCase(wayType);

        this.lengthLine = lengthLine;
        this.trueLength = trueLength > 0 ? trueLength : 0.1;
        this.curvature = (this.lengthLine > 0) ? this.trueLength / this.lengthLine : 1.0;

        this.surface = surface;
        this.wayType = wayType;
        this.smoothness = smoothness;
        this.startNodeId = startNodeId;
        this.finalNodeId = finalNodeId;

        this.hasCamera = randomHasCamera();
        if (this.hasCamera) {
            camera = new Camera(this);
            this.cameraLocation = randomCameraDistance();
        } else {
            this.cameraLocation = -1.0;
        }
    }

    public double getBaseFlow() {
        double infrastructureBonus = 1.0 + (Math.max(0, this.lanesNo - 1) * 0.1);
        return (this.speedLimit * infrastructureBonus) / this.curvature;
    }

    public double getTrafficFactor() {
        double trueLengthKm = Math.max(0.01, trueLength / 1000.0);
        double density = activeCars.get() / trueLengthKm;
        double optimalDensity = 45.0 * this.lanesNo;
        return Math.exp(-density / optimalDensity);
    }

    public double getWeatherFactor() {
        return switch (currentWeather) {
            case SNOWY -> 0.75;
            case RAINY -> 0.85;
            case SUNNY -> 1.1;
        };
    }

    // SKALOWANIE: Każda instancja Car reprezentuje Main.CAR_SCALE (10) samochodów na drodze
    public void enterWay() {
        activeCars.addAndGet(Main.CAR_SCALE);
    }

    public void leaveWay() {
        activeCars.addAndGet(-Main.CAR_SCALE);
    }

    public double getSpeedLimit() {
        return speedLimit;
    }

    public boolean getOneWay() {
        return isOneWay;
    }

    public double getTrueLength() {
        return trueLength;
    }

    public Long getFinalNodeId() {
        return finalNodeId;
    }

    public int getWayId() {
        return wayId;
    }

    public boolean getHasCamera() {
        return hasCamera;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public double getCameraLocation() {
        return cameraLocation;
    }

    public Long getStartNodeId() {
        return startNodeId;
    }

    public Intersection getStartNode() {
        return startNode;
    }

    public Intersection getFinalNode() {
        return finalNode;
    }

    public void setStartNode(Intersection startNode) {
        this.startNode = startNode;
    }

    public void setFinalNode(Intersection finalNode) {
        this.finalNode = finalNode;
    }

    public String getWayType() {
        return wayType;
    }

    public int getLanesNo() {
        return lanesNo;
    }

    public void updateDensity() {
        this.wayDensity = activeCars.get() / (trueLength * lanesNo);
    }

    public boolean isFull() {
        double maxCarsKmLane = 133.0;
        // Minimum 10 (Main.CAR_SCALE), aby pojedyncza reprezentacja 10 aut mogła wjechać na krótkie odcinki
        double capacity = Math.max(Main.CAR_SCALE, maxCarsKmLane * (trueLength / 1000.0) * lanesNo);
        return activeCars.get() >= capacity;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Way way = (Way) obj;
        return wayId == way.wayId && startNodeId == way.startNodeId && finalNodeId == way.finalNodeId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(wayId);
    }

    public void setCurrentWeather(Weather.weatherType currentWeather) {
        this.currentWeather = currentWeather;
    }

    public Weather.weatherType getCurrentWeather() {
        return currentWeather;
    }

    public String getSmoothness() {
        return smoothness;
    }

    public String getSurface() {
        return surface;
    }

    public double getCurvature() {
        return curvature;
    }

    public boolean isOneWay() {
        return isOneWay;
    }

    public boolean isLit() {
        return isLit;
    }

    public double getWayDensity() {
        return wayDensity;
    }
}