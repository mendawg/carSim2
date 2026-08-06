
import com.opencsv.CSVReader;

import java.io.FileReader;
import java.util.*;

public class DataLoader {




    private static boolean strToBoolean(String arg, boolean defaultValue) {
        if (arg == null || "unknown".equalsIgnoreCase(arg.trim())) return defaultValue;
        return arg.trim().equalsIgnoreCase("yes");
    }

    private static double strToDouble(String arg, double defaultValue) {
        if (arg == null || "unknown".equalsIgnoreCase(arg.trim())) return defaultValue;
        try {
            return Double.parseDouble(arg);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int strToInteger(String arg, int defaultValue) {
        if (arg == null || "unknown".equalsIgnoreCase(arg.trim())) return defaultValue;
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long strToLong(String arg, long defaultValue) {
        if (arg == null || "unknown".equalsIgnoreCase(arg.trim())) return defaultValue;
        try {
            return Long.parseLong(arg);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }


    public static List<Way> loadWays(String file) {
        List<Way> ways = new ArrayList<>();
        try (CSVReader csvReader = new CSVReader(new FileReader(file))) {

            String[] next;
            csvReader.readNext();

            while ((next = csvReader.readNext()) != null) {
                if (next.length != 12) throw new Exception();
                ways.add(new Way(
                        strToBoolean(next[5], false), //isOneWay
                        strToBoolean(next[6], true), //isLit
                        strToInteger(next[1], 50), //speedLimit
                        strToInteger(next[0], -1), //wayId
                        strToInteger(next[8],1), //lanesNo
                        strToDouble(next[4], 1.0), //lengthLine
                        strToDouble(next[3], 1.0), //trueLength
                        next[2], //surface
                        next[7], //wayType
                        next[9], //smoothness
                        strToLong(next[10], -1L), //startNodeId
                        strToLong(next[11], -1L)  //finalNodeId
                ));
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
        return ways;
    }

    public static Map<Long, Intersection> loadIntersections(String file){
        Map<Long, Intersection> interesections = new HashMap<>();
        try (CSVReader csvReader = new CSVReader(new FileReader(file))) {

            String[] next;
            csvReader.readNext();

            while ((next = csvReader.readNext()) != null) {
                if (next.length != 3) throw new Exception();
                interesections.put(strToLong(next[0], -1),
                        new Intersection(strToLong(next[0],-1),strToDouble(next[1],-2),strToDouble(next[2],-3)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return interesections;

    }
    public static List<Intersection> linkWays(String intersFile, String waysFile){

        Map<Long, Intersection> intersections = loadIntersections(intersFile);
        List<Way> ways = loadWays(waysFile);

        for (Way way : ways){

            Intersection start = intersections.get(way.getStartNodeId());
            Intersection end = intersections.get(way.getFinalNodeId());

            if (start == null || end == null) continue;

            way.setStartNode(start);
            way.setFinalNode(end);

            start.addOutgoingWay(way);
            end.addIncomingWay(way);

            if (!way.getOneWay()){
                end.addOutgoingWay(way);
                start.addIncomingWay(way);
            }
        }
        return new ArrayList<>(intersections.values());
    }


}
