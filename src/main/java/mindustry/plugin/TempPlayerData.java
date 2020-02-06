package mindustry.plugin;
import java.io.Serializable;

public class TempPlayerData implements Serializable {
    public Integer hue;
    public String realName = "";
    public TempPlayerData(Integer hue, String name){
        this.hue = hue;
        this.realName = name;
    }

    public void setHue(int i) { this.hue = i; }
}
