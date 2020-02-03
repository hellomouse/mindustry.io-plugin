package mindustry.plugin;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.text.SimpleDateFormat;
import java.util.*;

import mindustry.world.Block;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.json.JSONObject;
import org.json.JSONTokener;

import arc.Core;
import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.world.Tile;

import static mindustry.Vars.*;

public class IoPlugin extends Plugin {
    public static DiscordApi api = null;
    public static String prefix = ".";
    public static String serverName = "<untitled>";
    public static HashMap<String, Integer> database  = new HashMap<String, Integer>(); // uuid, rank
    public static HashMap<Player, PlayerData> rainbowedPlayers = new HashMap<Player, PlayerData>(); // player, PlayerData
    private final Long cooldownTime = 300L;
    private final String fileNotFoundErrorMessage = "File not found: config\\mods\\settings.json";
    private JSONObject alldata;
    public static JSONObject data; //token, channel_id, role_id
    private HashMap<Long, String> cooldowns = new HashMap<Long, String>(); //uuid

    //register event handlers and create variables in the constructor
    public IoPlugin() throws InterruptedException {
        try {
            String pureJson = Core.settings.getDataDirectory().child("mods/settings.json").readString();
            data = alldata = new JSONObject(new JSONTokener(pureJson));
        } catch (Exception e) {
            if (e.getMessage().contains(fileNotFoundErrorMessage)){
                Log.err("[ERR!] discordplugin: settings.json file is missing.\nBot can't start.");
                //this.makeSettingsFile("settings.json");
                return;
            } else {
                Log.err("[ERR!] discordplugin: Init Error");
                e.printStackTrace();
                return;
            }
        }
        try {
            api = new DiscordApiBuilder().setToken(alldata.getString("token")).login().join();
        }catch (Exception e){
            if (e.getMessage().contains("READY packet")){
                Log.err("\n[ERR!] discordplugin: invalid token.\n");
            } else {
                e.printStackTrace();
            }
        }
        BotThread bt = new BotThread(api, Thread.currentThread(), alldata);
        bt.setDaemon(false);
        bt.start();


        // setup database
        try {
            File toRead = new File("database.io");
            if(toRead.length() > 0) {
                FileInputStream fis = new FileInputStream(toRead);
                ObjectInputStream ois = new ObjectInputStream(fis);

                database = (HashMap<String, Integer>) ois.readObject();

                ois.close();
                fis.close();

                Log.info("discordplugin: database loaded successfully");
            }
        } catch(Exception e){
            e.printStackTrace();
        }

        // setup prefix
        if (data.has("prefix")) {
            prefix = String.valueOf(data.getString("prefix").charAt(0));
            api.updateActivity("prefix: " + prefix);
        } else {
            Log.warn("[WARN!] discordplugin: no prefix setting detected, using default \".\" prefix.");
        }

        // setup name
        if (data.has("server_name")) {
            serverName = String.valueOf(data.getString("server_name"));
        } else {
            Log.warn("[WARN!] discordplugin: no server_name setting detected.");
        }

        // setup database

        // live chat
        if (data.has("live_chat_channel_id")) {
            TextChannel tc = getTextChannel(data.getString("live_chat_channel_id"));
            if (tc != null) {
                HashMap<String, String> messageBuffer = new HashMap<>();
                Events.on(EventType.PlayerChatEvent.class, event -> {
                    messageBuffer.put(Utils.escapeCharacters(event.player.name), Utils.escapeCharacters(event.message));
                    if(messageBuffer.size() >= Utils.messageBufferSize) { // if message buffer size is below the expected size
                        EmbedBuilder eb = new EmbedBuilder().setTitle(new SimpleDateFormat("yyyy_MM_dd").format(Calendar.getInstance().getTime()));
                        for (Map.Entry<String, String> entry : messageBuffer.entrySet()) {
                            String username = entry.getKey();
                            String message = entry.getValue();
                            eb.addField(Utils.escapeCharacters(username), Utils.escapeCharacters(message));
                        }
                        tc.sendMessage(eb);
                        messageBuffer.clear();
                    }
                });
            }
        }


        // warning logs

        if (data.has("warnings_chat_channel_id")) {
            TextChannel tc = this.getTextChannel(data.getString("warnings_chat_channel_id"));
            if (tc != null) {
                HashMap<String, String> warnMessageBuffer = new HashMap<>();
                Events.on(EventType.TapConfigEvent.class, event -> {
                    if(event.player != null) {

                        warnMessageBuffer.put("Block: " + event.tile.block().name +  " Location: (" + event.tile.x + ", " + event.tile.y + ") Configuration: " + event.value, event.player.name);

                        if(warnMessageBuffer.size() >= Utils.messageBufferSize) { // if message buffer size is below the expected size
                            EmbedBuilder eb = new EmbedBuilder().setTitle("Logs from " + new SimpleDateFormat("yyyy_MM_dd").format(Calendar.getInstance().getTime()));
                            eb.setColor(Utils.Pals.warning);
                            eb.setTimestampToNow();
                            for (Map.Entry<String, String> entry : warnMessageBuffer.entrySet()) {
                                String message = entry.getKey();
                                String username = entry.getValue();
                                eb.addField(Utils.escapeCharacters(username), message);
                            }
                            tc.sendMessage(eb);
                            warnMessageBuffer.clear();
                        }
                    }
                });

            }
        }

        Events.on(EventType.PlayerLeave.class, event -> {
            if(rainbowedPlayers.containsKey(player)) {
                rainbowedPlayers.remove(player);
            }
        });

        // player joined
        Events.on(EventType.PlayerJoin.class, event -> {
            Player player = event.player;

            if(database.containsKey(player.uuid)) {
                int rank = database.get(player.uuid);
                if(rank==0) {
                    player.name.replaceAll("<", "");
                    player.name.replaceAll(">", "");
                }
                if(player.name.contains("<") && player.name.contains(">")) { // contains the tag
                    player.name = player.name.replaceFirst("\\[(.*)\\[\\] ", ""); // replace only the tag, no other colors
                }
                switch(rank) { // apply new tag
                    case 1:
                        Call.sendMessage("[sky]active player " + player.name + " joined the server!");
                        player.name = "[sky]<active>[] " + player.name;
                        break;
                    case 2:
                        Call.sendMessage("[#fcba03]vip " + player.name + " joined the server!");
                        player.name = "[#fcba03]<vip>[] " + player.name;
                        break;
                    case 3:
                        Call.sendMessage("[scarlet]moderator " + player.name + " joined the server!");
                        player.name = "[scarlet]<mod>[] " + player.name;
                        break;
                    case 4:
                        Call.sendMessage("[orange]<[][white]io admin[][orange]>[] " + player.name + " joined the server!");
                        player.name = "[orange]<[white]io[orange]>[] " + player.name;
                        break;
                }
            } else { // not in database
                player.name.replaceAll("<", "");
                player.name.replaceAll(">", "");
            }
        });

        Thread rainbowLoop = new Thread() {
            public void run() {
                do {
                    for (Map.Entry<Player, PlayerData> entry : rainbowedPlayers.entrySet()) {
                        Player p = entry.getKey();
                        if(!playerGroup.all().contains(p)){
                            rainbowedPlayers.remove(player);
                            return;
                        }
                        if(p==null) return;
                        if(p.name==null) return;
                        PlayerData pdata = entry.getValue();
                        String playerNameUnmodified = pdata.realName;
                        Integer hue = pdata.hue;
                        if(hue < 360) {
                            hue = hue + 1;
                        } else{
                            hue = 0;
                        }


                        Color hsb = Color.getHSBColor(hue / 360f, 1f, 1f);
                        pdata.setHue(hue);
                        String hex = "#" + Integer.toHexString(hsb.getRGB()).substring(2);
                        String[] c = playerNameUnmodified.split(" ", 2);

                        p.name = c[0] + " [" + hex + "]" + Utils.escapeColorCodes(c[1]);
                        rainbowedPlayers.replace(player, pdata);
                    }
                    try {
                        Thread.sleep(75);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } while (true);
            }
        };

        rainbowLoop.start();
    }

    //register commands that run on the server
    @Override
    public void registerServerCommands(CommandHandler handler){

    }

    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler){
        if (api != null) {
            handler.<Player>register("d", "<text...>", "Sends a message to moderators. (Please provide the griefer's name and the current server's ip.)", (args, player) -> {

                if (!data.has("warnings_chat_channel_id")) {
                    player.sendMessage("[scarlet]This command is disabled.");
                } else {
                    TextChannel tc = this.getTextChannel(data.getString("warnings_chat_channel_id"));
                    if (tc == null) {
                        player.sendMessage("[scarlet]This command is disabled.");
                        return;
                    }
                    tc.sendMessage(Utils.escapeCharacters(player.name) + " *@mindustry* : `" + args[0] + "`");
                    player.sendMessage("[scarlet]Successfully sent message to moderators.");
                }

            });

            handler.<Player>register("players", "Display all players and their ids", (args, player) -> {
                StringBuilder builder = new StringBuilder();
                builder.append("[orange]List of players: \n");
                for (Player p : Vars.playerGroup.all()) {
                    if(p.isAdmin) {
                        builder.append("[scarlet]<ADMIN> ");
                    } else{
                        builder.append("[lightgray] ");
                    }
                    builder.append(p.name).append("[accent] (#").append(p.id).append(")\n");
                }
                player.sendMessage(builder.toString());
            });

            handler.<Player>register("rainbow", "Give your username a rainbow animation [vip+ only]", (args, player) -> {
                if(database.containsKey(player.uuid)) {
                    if(database.get(player.uuid) >= 2) {
                        if(rainbowedPlayers.containsKey(player)) {
                            player.sendMessage("[sky]Rainbow effect toggled off.");
                            String nameToSet = rainbowedPlayers.get(player).realName;
                            rainbowedPlayers.remove(player);
                            player.name = nameToSet;
                        } else {
                            player.sendMessage("[sky]Rainbow effect toggled on.");
                            rainbowedPlayers.put(player, new PlayerData(player, 0, player.name));
                        }
                    } else {
                        player.sendMessage("You don't have permissions to execute this command!");
                    }
                } else {
                    player.sendMessage("You don't have permissions to execute this command!");
                }
            });

        }

    }



    public static TextChannel getTextChannel(String id){
        Optional<Channel> dc =  ((Optional<Channel>) api.getChannelById(id));
        if (!dc.isPresent()) {
            Log.err("[ERR!] discordplugin: channel not found!");
            return null;
        }
        Optional<TextChannel> dtc = dc.get().asTextChannel();
        if (!dtc.isPresent()){
            Log.err("[ERR!] discordplugin: textchannel not found!");
            return null;
        }
        return dtc.get();
    }

    public Role getRole(String id){
        Optional<Role> r1 = api.getRoleById(id);
        if (!r1.isPresent()) {
            Log.err("[ERR!] discordplugin: adminrole not found!");
            return null;
        }
        return r1.get();
    }

}