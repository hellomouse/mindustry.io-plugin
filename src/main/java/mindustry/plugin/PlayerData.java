package mindustry.plugin;

import java.io.*;

public class PlayerData implements Serializable {
    public Integer rank = 0;
    public Integer playTime = 0;
    public Integer buildingsBuilt = 0;
    public String playerName = "";
    public Integer gamesPlayed = 0;

    public PlayerData(Integer rank, String name){
        this.rank = rank;
        this.playerName = name;
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

    private void writeObject(ObjectOutputStream stream) throws IOException {
        stream.writeInt(rank);
        stream.writeInt(playTime);
        stream.writeInt(buildingsBuilt);
        stream.writeInt(gamesPlayed);
        stream.writeObject(playerName);
    }

    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
        rank = stream.readInt();
        playTime = stream.readInt();
        buildingsBuilt = stream.readInt();
        gamesPlayed = stream.readInt();
        playerName = (String) stream.readObject();
    }


}
