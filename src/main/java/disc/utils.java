package disc;


public class utils {
    static int nukeDistance = 25;
    public static Boolean antiNukeEnabled = true;;

    static double DistanceBetween(double x1, double y1, double x2, double y2) {
        return Math.sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1));
    }

    public static String escapeBackticks(String string){
        return string.replaceAll("```", "`\u200b`\u200b`");
    }

    public static String zeroWidthInterpolated(String string){
        String ret = "";
        for(int i = 0; i < string.length(); i++){
            ret += string.charAt(i) + '\u200b';
        }
        return ret;
    }
}