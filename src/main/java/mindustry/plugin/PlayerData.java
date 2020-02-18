package mindustry.plugin;

import java.io.Serializable;

public class PlayerData implements Serializable {
    public int rank;
    public int playTime = 0;
    public int buildingsBuilt = 0;
    public int enemiesKilled = 0;
    public int gamesPlayed = 0;
    public String usid;

    public PlayerData(String usid, Integer rank, Integer playTime) {
        this.usid = usid;
        this.rank = rank;
        this.playTime = playTime;
    }

    public PlayerData(String usid, int rank) {
        this.usid = usid;
        this.rank = rank;
    }

    public int getRank() { return rank; }

    public int getPlaytime() { return playTime; }

    public int getBuildings() { return buildingsBuilt; }


    public int getGames() { return gamesPlayed; }

    public void setRank(int rank) { this.rank = rank; }

    public void incrementPlaytime(int i) {
        this.playTime = playTime + i;
    }

    public void incrementBuilding(int i) {
        this.buildingsBuilt = buildingsBuilt + i;
    }

    public void incrementGames() {
        this.gamesPlayed = gamesPlayed + 1;
    }
}
