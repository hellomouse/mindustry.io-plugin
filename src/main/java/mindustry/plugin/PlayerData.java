package mindustry.plugin;

import java.io.Serializable;

public class PlayerData implements Serializable {
    public Integer hue;
    public String realName = "";
    public Integer rank = 0;
    public Integer playTime = 0;
    public Integer buildingsBuilt = 0;
    public Integer enemiesKilled = 0;
    public Integer draugPets = 0;
    public Integer gamesPlayed = 0;


    public PlayerData(Integer hue, String realName, Integer rank, Integer playTime){
        this.hue = hue;
        this.realName = realName;
        this.rank = rank;
        this.playTime = playTime;
    }

    public PlayerData(int i, String name) {
        this.hue = i;
        this.realName = name;
    }

    public PlayerData(int rank){
        this.rank = rank;
    }

    public int getRank() { return rank; }

    public int getPlaytime() { return playTime; }

    public int getBuildings() { return buildingsBuilt; }

    public int getDraugPets() { return draugPets; }

    public int getGames() { return gamesPlayed; }

    public void setRank(int rank) { this.rank = rank; }

    public void setHue(int h) {
        hue = h;
    }

    public void incrementPlaytime(int i) {
        this.playTime = playTime + i;
    }

    public void incrementBuilding(int i) {
        this.buildingsBuilt = buildingsBuilt + i;
    }

    public void incrementDraug(int i) {
        this.draugPets = draugPets + i;
    }

    public void resetDraug() {
        this.draugPets = 0;
    }

    public void incrementGames() {
        this.gamesPlayed = gamesPlayed + 1;
    }
}
