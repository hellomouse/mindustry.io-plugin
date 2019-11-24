package io.mindustry.plugin;

import java.util.List;

public class Utils {
    static int nukeDistance = 25;
    public static Boolean antiNukeEnabled = true;;

    static double DistanceBetween(double x1, double y1, double x2, double y2) {
        return Math.sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1));
    }

    public static String escapeBackticks(String string){
        return string.replaceAll("`", "");
    }

    public static String constructMessage(List<String> array) {
        StringBuilder result = new StringBuilder("```");
        for(String string : array){
            result.append(string).append("\n");
        }
        return result + "```";
    }
}
