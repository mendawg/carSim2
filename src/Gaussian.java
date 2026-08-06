import java.util.concurrent.ThreadLocalRandom;

public class Gaussian {

    public static double getRandomMultiplier() {
        double mean = 1.0;
        double stdDev = 0.0833;
        double min = 0.75;
        double max = 1.25;

        double result;

        do {
            double gaussian = ThreadLocalRandom.current().nextGaussian();

            result = mean + (gaussian * stdDev);

        } while (result < min || result > max);

        return result;
    }
}