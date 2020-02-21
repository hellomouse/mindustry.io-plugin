package mindustry.plugin;

import arc.util.Log;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.type.Player;
import mindustry.game.Rules;
import mindustry.gen.Call;
import mindustry.maps.Map;
import mindustry.net.Administration;
import mindustry.net.Administration.ActionType;
import mindustry.world.Block;
import mindustry.world.Tile;

import static mindustry.plugin.Utils.*;

public class MapRules {
    public static class Maps{
        public static String minefield = "Minefield"; // pvp map
    }


    public static void onMapLoad(){
        Rules rules = Vars.world.getMap().rules();
        Rules orig = rules.copy();
        rules.respawnTime = respawnTimeEnforced;
        Call.onSetRules(rules);

        Timer.schedule(() -> Call.onSetRules(orig), 1);

        Vars.netServer.admins.addActionFilter(action -> {
            Player player = action.player;
            // action was generated locally
            if (player == null) return true;
            String uuid = player.uuid;
            if (uuid == null) return true;

            // disable checks for moderators and admins
            if (ioMain.getRank(player) >= 4 || player.isAdmin) return true;

            if (ioMain.verifiedIPs.containsKey(uuid) && verification) {
                if (!ioMain.verifiedIPs.get(uuid)) {
                    Call.onInfoToast(player.con, "[scarlet]Your IP was flagged as a VPN, please join http://discord.mindustry.io and request manual verification.", 5f);
                    player.sendMessage("[#7a7a7a]Cannot build while flagged.");
                    return false;
                }
            }

            TempPlayerData tdata = ioMain.tempPlayerDatas.get(player.uuid);
            if (tdata == null) { // should never happen
                player.sendMessage("[scarlet]You may not build right now due to a server error, please tell an administrator");
                return false;
            }

            switch (action.type) {
                case rotate: {
                    boolean hit = tdata.rotateRatelimit.get();
                    if (hit) {
                        player.sendMessage("[scarlet]Rotate ratelimit exceeded, please rotate slower");
                        return false;
                    }
                    break;
                }

                case configure: {
                    boolean hit = tdata.configureRatelimit.get();
                    if (hit) {
                        player.sendMessage("[scarlet]Configure ratelimit exceeded, please configure slower");
                        return false;
                    }
                    break;
                }
            }

            return true;
        });
    }

    public static void run(){
        onMapLoad();
        Map map = Vars.world.getMap();
        if (map.name().equals(Maps.minefield)) {
            Log.info("[MapRules]: Minefield action trigerred.");
            Call.sendMessage("[scarlet]Preparing minefield map, please wait.");
            Tile[][] tiles = Vars.world.getTiles();
            for (int x = 0; x < tiles.length; ++x) {
                for(int y = 0; y < tiles[0].length; ++y) {
                    if (tiles[x][y] != null && tiles[x][y].entity != null) {
                        Block block = tiles[x][y].block();
                        if (block != null) {
                            if(block == Blocks.shockMine){
                                Call.onTileDamage(tiles[x][y], 30f); // damage mines 30hp, leaving them with 10hp
                            }
                        }
                    }
                }
            }
            Call.sendMessage("[scarlet]Minefield map generated, glhf!");
        }
    }
}
