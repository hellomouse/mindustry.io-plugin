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
    public static int phantomPetTeleportTime = 6; // in seconds
    static String welcomeMessage = "";
    static String statMessage = "mindustry[orange]<[white].io[orange]>[white]\n" +
            "\n" +
            "[sky]%player%'s stats:\n" +
            "[#4287f5]Playtime - %playtime% minutes.\n" +
            "[#f54263]Games played - %games%.\n" +
            "[#9342f5]Buildings built - %buildings%.";
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

    static double DistanceBetween(double x1, double y1, double x2, double y2) {
        return Math.sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1));
    }

    public static String escapeCharacters(String string){
        return escapeColorCodes(string.replaceAll("`", "").replaceAll("@", ""));
    }

    public static String escapeColorCodes(String string){
        return string.replaceAll("\\[(.*?)\\]", "");
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

    public static Player findPlayer(String identifier){
        Player found = null;
        for (Player player : playerGroup.all()) {
            if (player.con.address.equals(identifier) || String.valueOf(player.id).equals(identifier)) {
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
