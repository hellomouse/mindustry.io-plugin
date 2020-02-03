package mindustry.plugin;

import mindustry.entities.type.Player;

public class PlayerData {
    private final Player player;
    public Integer hue;
    public final String realName;

    public PlayerData(Player player, Integer hue, String realName){
        this.player = player;
        this.hue = hue;
        this.realName = realName;
    }

    public void setHue(int h) {
        hue = h;
    }
}
