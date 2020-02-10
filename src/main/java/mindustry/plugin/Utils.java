package mindustry.plugin;

import arc.struct.Array;
import mindustry.content.Blocks;
import mindustry.entities.type.Player;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.maps.Map;
import mindustry.world.Block;

import java.awt.*;
import java.util.HashMap;

import static mindustry.Vars.*;

public class Utils {
    static int messageBufferSize = 24; // number of messages sent at once to discord
    public static int chatMessageMaxSize = 256;
    public static float respawnTimeEnforced = 0.01f;
    public static int respawnTimeEnforcedDuration = 10; // duration of insta spawn
    static String welcomeMessage = "";
    static String noPermissionMessage = "You don't have permissions to execute this command! Purchase vip at https://donate.mindustry.io";
    static String statMessage = "";

    public static Schematic powerSchem;

    static String promotionMessage =  "mindustry[orange]<[white].io[orange]>[white]\n" +
            "\n" +
            "[sky]%player%, you have been promoted to [sky]<active>[]!\n" +
            "[#4287f5]You reached a playtime of - %playtime% minutes! That's 10+ hours!\n" +
            "[#f54263]You played a total of %games% games!\n" +
            "[#9342f5]You built a total of %buildings%!\n" +
            "[sky]Thank you for participating and enjoy your time on [orange]<[white]io[orange]>[sky]!\n"+
            "[scarlet]Please rejoin for the change to take effect.";

    static HashMap<Integer, String> rankNames = new HashMap<>();
    static HashMap<String, Integer> rankRoles = new HashMap<>();

    public static void init(){
        rankNames.put(0, "[#7d7d7d]<none>[]");
        rankNames.put(1, "[sky]<active player>[]");
        rankNames.put(2, "[#fcba03]<regular>[]");
        rankNames.put(3, "[scarlet]<donator>[]");
        rankNames.put(4, "[orange]<[][white]io moderator[][orange]>[]");
        rankNames.put(5, "[orange]<[][white]io administrator[][orange]>[]");

        rankRoles.put("627985513600516109", 1);
        rankRoles.put("636968410441318430", 2);
        rankRoles.put("674778262857187347", 3);
        rankRoles.put("624959361789329410", 4);

        activeRequirements.bannedBlocks.add(Blocks.conveyor);
        activeRequirements.bannedBlocks.add(Blocks.titaniumConveyor);
        activeRequirements.bannedBlocks.add(Blocks.junction);
        activeRequirements.bannedBlocks.add(Blocks.router);
        
        powerSchem = Schematics.readBase64("bXNjaAB4nEVQAQrDIBA79exgY+wNe0DfNNwqpeBUrKPs97NVM8G7YC4mSgMNgtibt6VbdJ958Y8YNptm6zWd11cycdyMc6SnJUyWTk+Ts01fuqzBmTRG462ja8rzWCQ2mRwSEd0JS7RNEkX9y84wCH0gUecLK6ljVXrV7kiB5eYhqla0iw6FbFouSOKsu8mq6EmOruCmMKeQipGKMcdIxUjFSMVI1b32nO11BfXf0PgN3V0Kqtof5zwb/Q==");
    }

    public static class Pals {
        public static Color warning = (Color.getHSBColor(5, 85, 95));
        public static Color info = (Color.getHSBColor(45, 85, 95));
        public static Color error = (Color.getHSBColor(3, 78, 91));
    }

    public static class activeRequirements {
        public static Array<Block> bannedBlocks = new Array<>();
        public static int playtime = 60 * 10;
        public static int buildingsBuilt = 1000 * 10;
        public static int gamesPlayed = 1 * 10;
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
            if (player.con.address.equals(identifier.replaceAll(" ", "")) || String.valueOf(player.id).equals(identifier.replaceAll(" ", "")) || player.uuid.equals(identifier.replaceAll(" ", "")) || Utils.escapeColorCodes(player.name.toLowerCase().replaceAll(" ", "")).equals(identifier.toLowerCase().replaceAll(" ", ""))) {
                found = player;
            }
        }
        return found;
    }

    public static String formatMessage(Player player, String message){
        message = message.replaceAll("%player%", escapeCharacters(player.name));
        message = message.replaceAll("%map%", world.getMap().name());
        message = message.replaceAll("%wave%", String.valueOf(state.wave));

        if(ioMain.database.containsKey(player.uuid)) {
            message = message.replaceAll("%playtime%", String.valueOf(ioMain.database.get(player.uuid).getPlaytime()));
            message = message.replaceAll("%games%", String.valueOf(ioMain.database.get(player.uuid).getGames()));
            message = message.replaceAll("%buildings%", String.valueOf(ioMain.database.get(player.uuid).getBuildings()));
            message = message.replaceAll("%rank%", escapeColorCodes(rankNames.get(ioMain.database.get(player.uuid).getRank())));
        }
        return message;
    }

}
