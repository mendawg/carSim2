import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

public class Car {

    private final AStar pathFinder;
    private Way currentWay;

    private static final double baseAcceleration = 1.8;
    private double currentSpeeed;
    private final double aggressivness;
    private double distTravelled;
    private final double dt;
    private final double acceleration;
    private final double deacceleration = 2.5;
    private double waitingTime = 0.0;
    private final double maxWaitTime;
    private double spawnWaitTime = 0.0;
    private final double maxSpawnWait;

    private final boolean cameraAware;
    private boolean arrived;
    private boolean reportMade = false;
    private boolean isSpawned = false;

    private final Queue<Main.Record> violationsQueue;
    private final ConcurrentMap<RouteKey, List<Way>> routeCache;

    private int rerouteAttempts = 0;
    private static final int MAX_REROUTE_ATTEMPTS = 5;
    private int currentWayIndex = 0;

    private Intersection start, dest;
    private List<Way> path;
    private final List<Intersection> intersections;

    private void chooseRoute() {
        int maxAttempts = 50;
        int attempts = 0;
        int size = intersections.size();
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        while (attempts < maxAttempts) {
            attempts++;

            start = intersections.get(rand.nextInt(size));
            dest = intersections.get(rand.nextInt(size));

            if (dest == null || start == null || start.equals(dest)) continue;
            // Szybkie odrzucenie węzłów bez dróg wyjściowych/wejściowych bez uruchamiania A*
            if (start.getOutgoingWays().isEmpty() || dest.getIncomingWays().isEmpty()) continue;

            RouteKey key = new RouteKey(start.getId(), dest.getId());

            // Nie używamy computeIfAbsent, aby nie blokować wątków podczas liczenia A*
            path = routeCache.get(key);
            if (path == null) {
                List<Way> computed = pathFinder.findPath(start, dest);
                path = (computed != null) ? computed : Collections.emptyList();
                routeCache.putIfAbsent(key, path);
            }

            if (!path.isEmpty()) break;
        }

        if (path == null || path.isEmpty()) {
            this.arrived = true;
            this.path = Collections.emptyList();
            return;
        }
        currentWayIndex = 0;
        currentWay = path.get(0);
    }

    public Car(List<Intersection> intersections, AStar pathFinder, double dt, Queue<Main.Record> violationsQueue,
               ConcurrentMap<RouteKey, List<Way>> routeCache) {
        this.intersections = intersections;
        this.pathFinder = pathFinder;
        this.violationsQueue = violationsQueue;
        this.routeCache = routeCache;

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        this.maxWaitTime = 90.0 + rand.nextDouble(25.0, 50.0);
        this.maxSpawnWait = 30.0 + rand.nextDouble(25.0, 50.0);

        this.arrived = false;
        this.distTravelled = 0.0;
        this.currentSpeeed = 0.0;
        this.dt = dt;
        this.aggressivness = Gaussian.getRandomMultiplier();
        this.cameraAware = rand.nextBoolean();
        this.acceleration = baseAcceleration * aggressivness;

        chooseRoute();
    }

    private void moveOnWay(double distance, double speedAtDistance) {
        double desiredSpeed = currentWay.getBaseFlow() * aggressivness;
        double driverDesiredSpeed = desiredSpeed * currentWay.getTrafficFactor() * currentWay.getWeatherFactor();
        driverDesiredSpeed = Math.max(driverDesiredSpeed, 1.0);

        double brakingDist = 0;
        if (currentSpeeed > speedAtDistance) {
            brakingDist = ((currentSpeeed * currentSpeeed) - (speedAtDistance * speedAtDistance)) / (2.0 * deacceleration);
            brakingDist += currentSpeeed * dt;
        }
        if (distance - distTravelled <= brakingDist) {
            currentSpeeed -= deacceleration * dt;
            currentSpeeed = Math.max(currentSpeeed, speedAtDistance);
        } else {
            if (currentSpeeed < driverDesiredSpeed) {
                currentSpeeed += acceleration * dt;
                currentSpeeed = Math.min(currentSpeeed, driverDesiredSpeed);
            } else if (currentSpeeed > driverDesiredSpeed) {
                currentSpeeed -= deacceleration * dt;
                currentSpeeed = Math.max(currentSpeeed, driverDesiredSpeed);
            }
        }
    }

    private void giveUp() {
        if (isSpawned && currentWay != null) {
            currentWay.leaveWay();
            currentWay.updateDensity();
            isSpawned = false;
        }
        arrived = true;
    }

    public void move(double absTime) {
        if (arrived) return;
        if (!isSpawned) {
            if (currentWay.isFull()) {
                spawnWaitTime += dt;
                if (spawnWaitTime >= maxSpawnWait) {
                    spawnWaitTime = 0.0;
                    tryReroute(start);
                }
                return;
            } else {
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
                if (tryReroute(currentWay.getFinalNode())) {
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

        // Generowanie 10 zróżnicowanych logów przy minięciu fotoradaru
        if (currentWay.getHasCamera() && !reportMade &&
                distTravelled >= currentWay.getCameraLocation()) {

            reportMade = true;
            ThreadLocalRandom rand = ThreadLocalRandom.current();

            double limit = currentWay.getSpeedLimit();
            double curvature = currentWay.getCurvature();
            double trueLength = currentWay.getTrueLength();
            String wayType = currentWay.getWayType();
            String smoothness = currentWay.getSmoothness();
            String surface = currentWay.getSurface();
            boolean isOneWay = currentWay.getOneWay();
            boolean isLit = currentWay.isLit();
            boolean isPrimary = currentWay.isPrimary();
            int lanesNo = currentWay.getLanesNo();
            int wayId = currentWay.getWayId();
            Weather.weatherType weather = currentWay.getCurrentWeather();
            double density = currentWay.getWayDensity();

            for (int i = 0; i < Main.CAR_SCALE; i++) {
                // Losujemy prędkość w przedziale [90%, 110%] prędkości zarejestrowanej
                double randomizedSpeed = currentSpeeed * rand.nextDouble(0.90, 1.10);
                violationsQueue.offer(new Main.Record(
                        absTime, randomizedSpeed, limit, curvature, trueLength,
                        wayType, smoothness, surface, isOneWay, isLit, isPrimary,
                        lanesNo, wayId, weather, density
                ));
            }
        }

        if (distTravelled >= currentWay.getTrueLength()) {
            if (nextWay == null) {
                giveUp();
                distTravelled = 0;
                currentSpeeed = 0;
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

    public boolean getArrived() {
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
        this.reportMade = false;
        rerouteAttempts = 0;
        return true;
    }
}