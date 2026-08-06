    import java.util.Random;
    import java.util.concurrent.ThreadLocalRandom;
    import java.util.concurrent.atomic.AtomicInteger;
    import java.util.random.RandomGenerator;

    public class Way {

        private boolean hasCamera;
        private boolean isOneWay;
        private boolean isLit;
        private boolean isPrimary;

        private double speedLimit;
        private int wayId;
        private int lanesNo;

        private final AtomicInteger activeCars = new AtomicInteger(0);

        private double lengthLine;
        private double trueLength;
        private double cameraLocation;
        private double realisticSpeedLimit;
        private double curvature;
        private volatile double wayDensity = 0;

        private String surface;
        private String wayType;
        private String smoothness;

        private Intersection startNode;
        private Intersection finalNode;

        private long startNodeId;
        private long finalNodeId;

        private Camera camera;

        private volatile Weather.weatherType currentWeather = Weather.weatherType.SUNNY;

    //TODO dodac element pogody, pogoda powinna miec wplyw na realna maksymalna predkosc, kretosc drogi takze

        private static boolean randomHasCamera(){

            return ThreadLocalRandom.current().nextDouble() < 0.10; //Tylko 10% drog bedzie mialo fotoradary
        }

        private Double randomCameraDistance()
        {
            return ThreadLocalRandom.current().nextDouble() * this.trueLength;
        }

        public Way(boolean isOneWay, boolean isLit, int speedLimit, int wayId, int lanesNo,
                   double lengthLine, double trueLength, String surface, String wayType, String smoothness
                ,Long startNodeId, Long finalNodeId
        ) {

            this.isOneWay = isOneWay;
            this.isLit = isLit;
            this.speedLimit = speedLimit / 3.6;
            this.wayId = wayId;
            this.lanesNo = lanesNo;
            this.isPrimary = wayType.toLowerCase().equals("primary");

            this.lengthLine = lengthLine;
            this.trueLength = trueLength;
            this.curvature = (this.lengthLine > 0) ?  this.trueLength / this.lengthLine : 1.0;

            this.surface = surface;
            this.wayType = wayType;
            this.smoothness = smoothness;
            this.startNodeId = startNodeId;
            this.finalNodeId = finalNodeId;

            this.hasCamera = randomHasCamera();
            if (this.hasCamera) {
                camera = new Camera(this);
                this.cameraLocation = randomCameraDistance();
            } else this.cameraLocation = -1.0;
        }

        public double getBaseFlow(){
            double infrastructureBonus = 1.0 + (Math.max(0, this.lanesNo - 1) * 0.1);

            return (this.speedLimit * infrastructureBonus) / this.curvature;
        }

        public double getTrafficFactor(){
            double trueLengthKm = trueLength / 1000;
            if (trueLengthKm < 0.01) trueLengthKm = 0.01;
            double density = activeCars.get() / trueLengthKm;
            double optimalDensity = 45 * this.lanesNo;
            return Math.exp(-density / optimalDensity);
        }

        public double getWeatherFactor(){
            return switch (currentWeather){
                case SNOWY -> 0.75;
                case RAINY -> 0.85;
                case SUNNY -> 1.1;
                default -> throw new IllegalArgumentException();
            };
        }




    //
    //    public Double getRealisticSpeedLimit() {
    //
    //        double freeFlow = (this.speedLimit / this.curvature); // * this.weatherFactor
    //        double trueLengthKm = trueLength / 1000;
    //
    //        if (trueLengthKm < 0.01) trueLengthKm = 0.01;
    //
    //        double density = activeCars.get() / trueLengthKm;
    //        double optimalDensity = 45 * this.lanesNo;
    //        double trafficMultiplier = Math.exp(-density / optimalDensity);
    //
    //        return freeFlow * trafficMultiplier;
    //    }

        public void enterWay(){
            activeCars.incrementAndGet();
        }
        public void leaveWay(){
            activeCars.decrementAndGet();
        }

        public Double getSpeedLimit() {
            return speedLimit;
        }

        public Boolean getOneWay() {
            return isOneWay;
        }

        public Double getTrueLength() {
            return trueLength;
        }

        public Long getFinalNodeId() {
            return finalNodeId;
        }

        public Integer getWayId() {
            return wayId;
        }

        public Boolean getHasCamera() {
            return hasCamera;
        }

        public boolean isPrimary() {
            return isPrimary;
        }

        public Double getCameraLocation() {
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
        public void updateDensity(){
            if (trueLength <= 0) trueLength = 0.1;
            this.wayDensity = activeCars.get() / (trueLength * lanesNo);
        }
        public boolean isFull(){
            double maxCarsKmLane = 133;
            int ac = activeCars != null ? activeCars.get() : 0;
            return activeCars.get() >= Math.max(1,maxCarsKmLane * (trueLength / 1000) * lanesNo) ;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Way way = (Way) obj;
            return wayId == way.getWayId();
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
