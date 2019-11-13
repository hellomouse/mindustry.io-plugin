package disc;

public class utils {
    static int nukeDistance = 15;
    public static Boolean antiNukeEnabled = true;;

    static double DistanceBetween(double x1, double y1, double x2, double y2) {
        return Math.sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1));
    }
}
