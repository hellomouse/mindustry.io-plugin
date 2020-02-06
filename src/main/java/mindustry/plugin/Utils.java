package mindustry.plugin;

import mindustry.entities.type.Player;
import mindustry.maps.Map;

import java.awt.*;
import java.util.HashMap;
import java.util.List;

import static mindustry.Vars.*;

public class Utils {
    static int messageBufferSize = 24; // number of messages sent at once to discord
    public static int chatMessageMaxSize = 256;
    public static int phantomPetTeleportTime = 1; // in seconds
    static String welcomeMessage = "";
    static String noPermissionMessage = "You don't have permissions to execute this command! Purchase vip at https://donate.mindustry.io";
    static String statMessage = "mindustry[orange]<[white].io[orange]>[white]\n" +
            "\n" +
            "[sky]%player%'s stats:\n" +
            "[#4287f5]Playtime - %playtime% minutes.\n" +
            "[#f54263]Games played - %games%.\n" +
            "[#9342f5]Buildings built - %buildings%.";

    static String promotionMessage =  "mindustry[orange]<[white].io[orange]>[white]\n" +
            "\n" +
            "[sky]%player%, you have been promoted to [sky]<active>[]!\n" +
            "[#4287f5]You reached a playtime of - %playtime% minutes! That's 10+ hours!\n" +
            "[#f54263]You played a total of %games% games!\n" +
            "[#9342f5]You built a total of %buildings%!\n" +
            "[sky]Thank you for participating and enjoy your time on [orange]<[white]io[orange]>[sky]!";

    static HashMap<Integer, String> rankNames = new HashMap<>();

    public static void init(){
        rankNames.put(0, "[#7d7d7d]<none>[]");
        rankNames.put(1, "[sky]<active player>[]");
        rankNames.put(2, "[#fcba03]<vip>[]");
        rankNames.put(3, "[scarlet]<mvp>[]");
        rankNames.put(4, "[orange]<[][white]io moderator[][orange]>[]");
        rankNames.put(5, "[orange]<[][white]io administrator[][orange]>[]");
    }

    public static class Pals {
        public static Color warning = (Color.getHSBColor(5, 85, 95));
        public static Color info = (Color.getHSBColor(45, 85, 95));
        public static Color error = (Color.getHSBColor(3, 78, 91));
    }

    public static class activeRequirements {
        public static int playtime = 60 * 10;
        public static int buildingsBuilt = 400 * 10;
        public static int gamesPlayed = 1 * 10;
    }

    static double DistanceBetween(double x1, double y1, double x2, double y2) {
        return Math.sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1));
    }

    public static String escapeCharacters(String string){
        return escapeColorCodes(string.replaceAll("`", "").replaceAll("@", ""));
    }

    public static String escapeColorCodes(String string){
        return string.replaceAll("\\[(.*?)\\]", "");
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

    public static Player findPlayer(String identifier){
        Player found = null;
        for (Player player : playerGroup.all()) {
            if (player.con.address.equals(identifier) || String.valueOf(player.id).equals(identifier) || player.uuid.equals(identifier) || Utils.escapeColorCodes(player.name.toLowerCase()).equals(identifier.toLowerCase())) {
                found = player;
            }
        }
        return found;
    }

    public static String formatMessage(Player player, String message){
        message = message.replaceAll("%player%", escapeCharacters(player.name));
        message = message.replaceAll("%map%", world.getMap().name());
        message = message.replaceAll("%wave%", String.valueOf(state.wave));

        if(IoPlugin.database.containsKey(player.uuid)) {
            message = message.replaceAll("%playtime%", String.valueOf(IoPlugin.database.get(player.uuid).getPlaytime()));
            message = message.replaceAll("%games%", String.valueOf(IoPlugin.database.get(player.uuid).getGames()));
            message = message.replaceAll("%buildings%", String.valueOf(IoPlugin.database.get(player.uuid).getBuildings()));
            message = message.replaceAll("%rank%", rankNames.get(IoPlugin.database.get(player.uuid).getRank()));
        }
        return message;
    }

}
