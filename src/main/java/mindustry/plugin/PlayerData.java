package mindustry.plugin;

import java.io.Serializable;

public class PlayerData implements Serializable {
    public Integer rank = 0;
    public Integer playTime = 0;
    public Integer buildingsBuilt = 0;
    public Integer enemiesKilled = 0;
    public Integer gamesPlayed = 0;


    public PlayerData(Integer rank, Integer playTime){
        this.rank = rank;
        this.playTime = playTime;
    }

    public PlayerData(int rank){
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
