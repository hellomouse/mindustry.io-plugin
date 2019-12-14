package io.mindustry.plugin;

import io.anuke.mindustry.entities.type.Player;
import java.util.ArrayList;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.IOException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class TempBan {

    private static JSONObject bans;

    private TempBan() throws IOException { // you may notice many try catches, please ignore. also, might fail on first runthrough, not sure
    }

    public static JSONObject getBans() {
        JSONObject get = new JSONObject(); // create a default get jsonobject incase json file was just created
        try {
            File banfile = new File("bans.json");
            if (!banfile.exists()) {
                banfile.createNewFile(); // if there is no bans.json, a new one is created
            }
            FileReader filereader = new FileReader(banfile);
            JSONParser parser = new JSONParser();
            get = (JSONObject) (parser.parse(filereader)); //parses file for a jsonobject and set get to it
        } catch (Exception e) {

        }
        return get;
    }

    public static ArrayList<String> getBanArrayList() {
        bans = getBans();
        ArrayList<String> get = new ArrayList<>();
        try {
            for (Object i : bans.keySet()) {
                get.add(((String) i));
            }
        } catch (Exception e) {
        }
        return get;
    }

    public static String getBanTime(String ip) {
        bans = getBans();
        String out = "how did you get this message you have no ban time what";
        for (Object i : bans.keySet()) {
            if (ip.equals(((String) i))) {
                out = "" + (((float) (((long) bans.get(i)) - System.currentTimeMillis())) / 1000);
            }
        }
        return out;
    }

    public static void addBan(Player p, double minutes) {// using float allows for somewhat more precise bans
        // hopefully this works
        long milliseconds = (long) (minutes * 60000); //typecasts the float into long
        try {
            bans = getBans();
            bans.put(p.con.address, ((milliseconds + System.currentTimeMillis())));// gets current unix time, adds time to be tempbanned to it, and adds it it to jsonobject with the ip of the player
            writeBans();
        } catch (Exception e) {
        }
    }

    public static boolean removeBan(String ip) {
        boolean output = false;
        bans = getBans();
        try {
            bans.remove(ip);//removes ip from object, probably works
            output = true;
        } catch (Exception e) {
        }
        writeBans();
        return output;
    }

    private static void writeBans() {

        try {
            File banfile = new File("bans.json");
            FileWriter filewriter = new FileWriter(banfile);
            filewriter.write(bans.toJSONString());//turns json object into a jsonstring, and writes it to json file
            filewriter.flush();
        } catch (Exception e) {
        }
    }

    public static void updatebans() {
        ArrayList<String> toremove = new ArrayList<>();
        bans = getBans();
        for (Object i : bans.keySet()) {
            if (((long) bans.get(i)) <= System.currentTimeMillis()) {// checks if current time is greater than or equal to to be unbanned time, and if it is unbans them and removes them from the json
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
