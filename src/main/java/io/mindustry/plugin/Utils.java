package io.mindustry.plugin;

import io.anuke.mindustry.maps.Map;

import java.util.List;

import static io.anuke.mindustry.Vars.maps;

public class Utils {
    static int nukeDistance = 25;
    static int messageBufferSize = 5; // number of messages sent at once to discord
    public static int chatMessageMaxSize = 200;
    public static Boolean antiNukeEnabled = true;;

    static double DistanceBetween(double x1, double y1, double x2, double y2) {
        return Math.sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1));
    }

    public static String escapeBackticks(String string){
        return string.replaceAll("`", "");
    }

    public static String constructMessage(List<String> array) {
        StringBuilder result = new StringBuilder();
        for(String string : array){
            result.append(string).append("\n");
        }
        return result.toString();
    }

    public static String stringArrayToString(List<String> array) {
        StringBuilder result= new StringBuilder();
        for(String string : array){
            result.append(string);
        }
        return result.toString();
    }

    public static Map getMapBySelector(String query) {
        Map found = null;
        try {
            // try by number
            found = maps.customMaps().get(Integer.parseInt(query));
        } catch (Exception e) {
            // try by name
            for (Map m : maps.customMaps()) {
                if (m.name().equals(query)) {
                    found = m;
                    break;
                }
            }
        }
        return found;
    }
}
