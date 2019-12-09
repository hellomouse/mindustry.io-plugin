package io.mindustry.plugin;

import io.anuke.mindustry.entities.type.Player;
import static io.anuke.mindustry.Vars.*;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.io.FileWriter;
import java.io.FileReader;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;



public class TempBan {

    private static JSONObject bans;
    private static File banfile;
     
    

    public TempBan() throws Exception {
        banfile = new File("bans.json");
        if(!banfile.exists()) banfile.createNewFile(); // if there is no bans.json, a new one is created
        this.bans = getBans();
        // retrieve bans from json
    }

    private JSONObject getBans() throws Exception {
        
        FileReader filereader= new FileReader(banfile);
        JSONParser parser = new JSONParser();
        JSONObject get=(JSONObject)(parser.parse(filereader));
        // get bans from json
        filereader.close();
        return get;
    }

    public void addBan(Player p, long minutes) {
        // hopefully this works
        long milliseconds = minutes * 60000;
        bans.put("" + p.con.address, ((milliseconds + System.currentTimeMillis())));
    }
    public boolean removeBan(String ip) {
        boolean output=false;
        try{
            bans.remove(ip);
            output=true;
        }
        catch(Exception e){}
        return output;
    }
    
    private static void writeBans() throws Exception {
        FileWriter filewriter= new FileWriter(banfile);
        banfile.delete();
        banfile.createNewFile();
        try{
            filewriter.write(bans.toJSONString());
            
        }
        catch(Exception exc){}
        filewriter.flush();
        filewriter.close();
    }

    public static void updatebans() {
        ArrayList<String> toremove = new ArrayList<>();
        for (Object i : bans.keySet()) {
            if ((Integer)bans.get(i) >= System.currentTimeMillis()) {
                netServer.admins.unbanPlayerIP((String)i);
                toremove.add((String)i);
            }
        }
        for (String i : toremove) {
            bans.remove(i);
        }
    }

    public static void update() throws Exception {
        updatebans();
        writeBans();
    }
}
