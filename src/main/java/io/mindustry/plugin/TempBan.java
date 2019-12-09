package io.mindustry.plugin;

import io.anuke.mindustry.entities.type.Player;
import static io.anuke.mindustry.Vars.*;
import java.util.ArrayList;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.IOException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class TempBan {

    private static JSONObject bans;
    private static File banfile;

    public TempBan() throws IOException { // you may notice many try catches, please ignore. also, might fail on first runthrough, not sure
        banfile = new File("bans.json");
        if (!banfile.exists()) {
            banfile.createNewFile(); // if there is no bans.json, a new one is created
        } 
        this.bans = getBans();
        // retrieve bans from json
    }

    private JSONObject getBans() {
        JSONObject get = new JSONObject(); // create a default get jsonobject incase json file was just created
        try {
            FileReader filereader = new FileReader(banfile);
            JSONParser parser = new JSONParser();
            get = (JSONObject) (parser.parse(filereader)); //parses file for a jsonobject and set get to it
        } catch (Exception e) {
        }
        return get;
    }

    public void addBan(Player p, float minutes) {// using float allows for somewhat more precise bans
        // hopefully this works
        long milliseconds = (long) (minutes * 60000); //typecasts the float into long
        try {
            bans.put(p.con.address, ((milliseconds + System.currentTimeMillis())));// gets current unix time, adds time to be tempbanned to it, and adds it it to jsonobject with the ip of the player
        } catch (Exception e) {
        }
    }

    public boolean removeBan(String ip) {
        boolean output = false;
        try {
            bans.remove(ip);//removes ip from object, probably works
            output = true;
        } catch (Exception e) {
        }
        return output;
    }

    public static void writeBans() {

        try {
            FileWriter filewriter = new FileWriter(banfile);
            filewriter.write(bans.toJSONString());//turns json object into a jsonstring, and writes it to json file
            filewriter.flush();
        } catch (Exception exc) {
        }
    }

    public static void updatebans() {
        ArrayList<String> toremove = new ArrayList<>();
        for (Object i : bans.keySet()) {
            System.out.println((long) bans.get(i));
            if (((long) bans.get(i)) <= System.currentTimeMillis()) {// checks if current time is greater than or equal to to be unbanned time, and if it is unbans them and removes them from the json
                netServer.admins.unbanPlayerIP((String)i);//unban code
                toremove.add((String) i);// due to enhanced for loop being unable to be edited while it is being looped to, i made a list to add bans to be removed
            }

        }
        for (String i : toremove) { // goes through list of bans to be removed and removes them
            bans.remove(i);
        }
    }

    public static void update() {
        try {
            updatebans();// goes through update bans to check if bans are to be removed, then writes the new bans to json file
            writeBans();
        } catch (Exception e) {
        }
    }
}
