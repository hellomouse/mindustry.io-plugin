package mindustry.plugin;

import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.type.Player;
import mindustry.game.Rules;
import mindustry.gen.Call;
import mindustry.maps.Map;
import mindustry.net.Administration;
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

        Thread normalRules = new Thread() {
            public void run() {
                try {
                    Thread.sleep(1000 * respawnTimeEnforcedDuration);
                    Call.onSetRules(orig);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        normalRules.run();

        Administration.ActionFilter filter = new Administration.ActionFilter() {
            @Override
            public boolean allow(Administration.PlayerAction playerAction) {
                if(playerAction.type == Administration.ActionType.rotate) {
                    return false;
                }
                Player player = playerAction.player;
                String uuid = null;
                if(player!=null){ uuid = player.uuid; }

                if(uuid==null){
                    return true;
                }

                if(ioMain.verifiedIPs.containsKey(uuid) && verification){
                    if(!ioMain.verifiedIPs.get(uuid)){
                        Call.onInfoToast(player.con, "[scarlet]Your IP was flagged as a VPN, please join http://discord.mindustry.io and request manual verification.", 5f);
                        playerAction.player.sendMessage("[#7a7a7a]Cannot build while flagged.");
                        return false;
                    }
                }
                return true;
            }
        };
        Vars.netServer.admins.addActionFilter(filter);
    }

    public static void run(){
        onMapLoad();
        Map map = Vars.world.getMap();
        if (map.name().equals(Maps.minefield)) {
            Log.info("[MapRules]: Minefield action trigerred.");
            Call.sendMessage("[scarlet]Preparing minefield map, please wait.");
            Tile[][] tiles = Vars.world.getTiles();
            for(int x = 0; x < tiles.length; ++x) {
                for(int y = 0; y < tiles[0].length; ++y) {
                    if (tiles[x][y] != null && tiles[x][y].entity != null) {
                        Block block = tiles[x][y].block();
                        if(block!=null){
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
