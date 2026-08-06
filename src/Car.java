import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Queue;

public class Car {


    private final AStar pathFinder;

    private Way currentWay;

    private static double baseAcceleration = 1.8;
    private double currentSpeeed;
    private final double aggressivness;
    private double distTravelled;
    private final double dt;
    private final double acceleration;
    private final double deacceleration = 2.5;
    private double waitingTime = 0.0;
    private final double maxWaitTime = 90 + ThreadLocalRandom.current().nextDouble(25,50);
    private double spawnWaitTime = 0.0;
    private final double maxSpawnWait = 30.0 + ThreadLocalRandom.current().nextDouble(25,50);

    private boolean cameraAware;
    private boolean arrived;
    private boolean reportMade = false;
    private boolean waiting = false;
    private boolean isSpawned = false;

    private final Queue<Main.Record> violationsQueue;
    private final ConcurrentMap<RouteKey, List<Way>> routeCache;

    private int rerouteAttempts = 0;
    private static final int MAX_REROUTE_ATTEMPTS = 5;
    private int currentWayIndex = 0;

    private Intersection start,dest;

    private List<Way> path;

    private final List<Intersection> intersections;

    private void chooseRoute(){
        int maxAttempts = 50;
        int attempts = 0;
        while(attempts < maxAttempts){
            attempts++;

            start = intersections.get(ThreadLocalRandom.current().nextInt(intersections.size()));
            dest = intersections.get(ThreadLocalRandom.current().nextInt(intersections.size()));

            if (dest == null || start == null || start.equals(dest)) continue;

            RouteKey key = new RouteKey(start.getId(), dest.getId());


            path = routeCache.computeIfAbsent(key, k -> pathFinder.findPath(start, dest));

            if (path != null && !path.isEmpty()) break;

        }
        if (path == null || path.isEmpty()) {
            this.arrived = true;
            reportArrival();
            this.path = Collections.emptyList();
            return;
        }
        currentWayIndex = 0;
        currentWay = path.get(currentWayIndex);

    }

    public Car(List<Intersection> intersections, AStar pathFinder, double dt, Queue<Main.Record> violationsQueue,
               ConcurrentMap<RouteKey, List<Way>> routeCache) {
        this.intersections = intersections;
        this.pathFinder = pathFinder;
        this.violationsQueue = violationsQueue;
        this.routeCache = routeCache;

        arrived = false;
        distTravelled = 0.0;
        currentSpeeed = 0.0;
        this.dt = dt;
        aggressivness = Gaussian.getRandomMultiplier();
        cameraAware = ThreadLocalRandom.current().nextBoolean();
        acceleration = baseAcceleration * aggressivness;

        chooseRoute();
    }

    private void moveOnWay(double distance, double speedAtDistance){
        double desiredSpeed = currentWay.getBaseFlow() * aggressivness;
        double driverDesiredSpeed = desiredSpeed * currentWay.getTrafficFactor() * currentWay.getWeatherFactor();
        driverDesiredSpeed = Math.max(driverDesiredSpeed, 1.0);

        double brakingDist = 0;
        if (currentSpeeed > speedAtDistance){
            brakingDist = (Math.pow(currentSpeeed,2) - Math.pow(speedAtDistance, 2)) / (2 * deacceleration);
            brakingDist += currentSpeeed * dt;
        }
        if(distance - distTravelled <= brakingDist){
            currentSpeeed -= deacceleration * dt;
            currentSpeeed = Math.max(currentSpeeed,speedAtDistance);
        }else{
            if (currentSpeeed < driverDesiredSpeed){
                currentSpeeed += acceleration * dt;
                currentSpeeed = Math.min(currentSpeeed, driverDesiredSpeed);
            }else if (currentSpeeed > driverDesiredSpeed){
                currentSpeeed -= deacceleration * dt;
                currentSpeeed = Math.max(currentSpeeed, driverDesiredSpeed);
            }
        }

    }

    private void reportArrival(){

    }
    private void giveUp() {
        if (isSpawned && currentWay != null) {
            currentWay.leaveWay();
            currentWay.updateDensity();
            isSpawned = false;
        }
        arrived = true;
        reportArrival();
    }

    public void move(double absTime) {
        if (arrived) return;
        if (!isSpawned){
            if(currentWay.isFull()){
                spawnWaitTime += dt;
                if (spawnWaitTime >= maxSpawnWait){
                    spawnWaitTime = 0.0;
                    tryReroute(start);
                }
                return;
            } else{
                currentWay.enterWay();
                currentWay.updateDensity();
                isSpawned = true;
            }
        }

        double distanceToTravel;
        double desiredSpeedAtPoint;

        Intersection currentTarget = currentWay.getFinalNode();
        boolean green = currentTarget.hasGreenLight(currentWay, absTime);


        Way nextWay = (currentWayIndex + 1 < path.size()) ? path.get(currentWayIndex + 1) : null;

        boolean blockedByCongestion = (nextWay != null && nextWay.isFull());
        boolean reactingToCamera = currentWay.getHasCamera() && cameraAware &&
                distTravelled < currentWay.getCameraLocation();

        if (!reactingToCamera) {
            distanceToTravel = currentWay.getTrueLength();
            if (!green || blockedByCongestion) {
                desiredSpeedAtPoint = 0.0;
            } else {
                desiredSpeedAtPoint = currentWay.getSpeedLimit() * currentWay.getTrafficFactor() *
                        currentWay.getWeatherFactor();
            }
        } else {
            distanceToTravel = currentWay.getCameraLocation();
            desiredSpeedAtPoint = currentWay.getSpeedLimit() * currentWay.getTrafficFactor() *
                    currentWay.getWeatherFactor();
        }

        if (currentSpeeed < 0.5 && blockedByCongestion) {
            waitingTime += dt;
            if (waitingTime >= maxWaitTime) {
                waitingTime = 0.0;
                Way oldWay = currentWay;
                if (tryReroute(currentWay.getFinalNode())){
                    oldWay.leaveWay();
                    oldWay.updateDensity();
                    currentWay.enterWay();
                    currentWay.updateDensity();

                    blockedByCongestion = false;
                    nextWay = path.size() > 1 ? path.get(1) : null;
                }
            }
        } else if (currentSpeeed > 2.0) {
            waitingTime = 0.0;
        }
        if (arrived) return;
        moveOnWay(distanceToTravel, desiredSpeedAtPoint);
        distTravelled += currentSpeeed * dt;
        if (currentWay.getHasCamera() && !reportMade &&
                distTravelled >= currentWay.getCameraLocation()) {

            reportMade = true;
            violationsQueue.offer(new Main.Record(absTime, currentSpeeed, currentWay.getSpeedLimit(),
                    currentWay.getCurvature(), currentWay.getTrueLength(), currentWay.getWayType(),
                    currentWay.getSmoothness(), currentWay.getSurface(), currentWay.getOneWay(), currentWay.isLit(),
                    currentWay.isPrimary(), currentWay.getLanesNo(), currentWay.getWayId(), currentWay.getCurrentWeather(),
                    currentWay.getWayDensity()));
        }

        if (distTravelled >= currentWay.getTrueLength()) {
            if (nextWay == null) {
                giveUp();
                distTravelled = 0;
                currentSpeeed = 0;
//                currentWay.leaveWay();
            } else if (!blockedByCongestion) {
                distTravelled -= currentWay.getTrueLength();

                currentWay.leaveWay();
                currentWay.updateDensity();

                currentWayIndex++;
                currentWay = nextWay;

                currentWay.enterWay();
                currentWay.updateDensity();
                reportMade = false;
            } else {

                distTravelled = currentWay.getTrueLength();
                currentSpeeed = 0.0;
            }
        }
    }
    public void printDiagnostics() {
        System.out.printf("   Status celu: %s\n", arrived ? "Osiągnięty" : "W drodze");
        System.out.printf("   Aktualna prędkość: %.2f m/s\n", currentSpeeed);
        System.out.printf("   Przebyty dystans na obecnym odcinku: %.2f m\n", distTravelled);
    }
    public boolean getArrived(){
        return arrived;
    }

    private boolean tryReroute(Intersection from) {
        rerouteAttempts++;
        if (rerouteAttempts > MAX_REROUTE_ATTEMPTS) {
            giveUp();
            return false;
        }

        List<Way> newPath = pathFinder.findPath(from, dest);
        if (newPath == null || newPath.isEmpty()) {
            return false;
        }

        this.path = newPath;
        this.currentWayIndex = 0;
        this.currentWay = newPath.get(0);
        rerouteAttempts = 0;
        return true;
    }


}
