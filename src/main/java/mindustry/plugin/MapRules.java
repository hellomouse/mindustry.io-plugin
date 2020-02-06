package mindustry.plugin;

import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.gen.Call;
import mindustry.maps.Map;
import mindustry.world.Block;
import mindustry.world.Tile;

public class MapRules {
    public static class Maps{
        public static String minefield = "Minefield"; // pvp map
    }

    public static void run(){
        Map map = Vars.world.getMap();
        if (map.name().equals(Maps.minefield)) {
            Call.sendMessage("[scarlet]Preparing minefield map, please wait.");
            Tile[][] tiles = Vars.world.getTiles();
            for(int x = 0; x < tiles.length; ++x) {
                for(int y = 0; y < tiles[0].length; ++y) {
                    if (tiles[x][y] != null && tiles[x][y].entity != null) {
                        Block block = tiles[x][y].block();
                        if(block!=null){
                            if(block == Blocks.shockMine){
                                block.health = 10; // set all mines health on the minefield map to 10
                            }
                        }
                    }
                }
            }
            Call.sendMessage("[scarlet]Minefield map generated, glhf!");
        }
    }
}
