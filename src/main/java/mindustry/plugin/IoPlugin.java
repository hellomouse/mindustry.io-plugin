package mindustry.plugin;

import java.awt.Color;
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
    public static HashMap<String, Integer> database  = new HashMap<String, Integer>();
    private final Long cooldownTime = 300L;
    private final String fileNotFoundErrorMessage = "File not found: config\\mods\\settings.json";
    private JSONObject alldata;
    public static JSONObject data; //token, channel_id, role_id
    private HashMap<Long, String> cooldowns = new HashMap<Long, String>(); //uuid

    //register event handlers and create variables in the constructor
    public IoPlugin() throws InterruptedException {
        try {
            String pureJson = Core.settings.getDataDirectory().child("mods/settings.json").readString();
            alldata = new JSONObject(new JSONTokener(pureJson));
            if (!alldata.has("in-game")){
                Log.err("[ERR!] discordplugin: settings.json has an invalid format!\n");
                //this.makeSettingsFile("settings.json");
                return;
            } else {
                data = alldata.getJSONObject("in-game");
            }
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
            api.updateActivity("prefix: " + prefix);
        }catch (Exception e){
            if (e.getMessage().contains("READY packet")){
                Log.err("\n[ERR!] discordplugin: invalid token.\n");
            } else {
                e.printStackTrace();
            }
        }
        BotThread bt = new BotThread(api, Thread.currentThread(), alldata.getJSONObject("discord"));
        bt.setDaemon(false);
        bt.start();


        // setup database
        try {
            File toRead = new File("database.io");
            FileInputStream fis = new FileInputStream(toRead);
            ObjectInputStream ois = new ObjectInputStream(fis);

            database = (HashMap<String, Integer>) ois.readObject();

            ois.close();
            fis.close();

            Log.info("discordplugin: database loaded successfully");
        } catch(Exception e){
            e.printStackTrace();
        }

        // setup prefix
        if (data.has("prefix")) {
            prefix = String.valueOf(data.getString("prefix").charAt(0));
        } else {
            Log.warn("[WARN!] discordplugin: no prefix setting detected, using default \".\" prefix.");
        }

        // setup name
        if (data.has("server_name")) {
            serverName = String.valueOf(data.getString("server_name").charAt(0));
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

        // anti nuke

        Events.on(EventType.BuildSelectEvent.class, event -> {
            if (Utils.antiNukeEnabled) {
                try {
                    Tile nukeTile = event.builder.buildRequest().tile();
                    if (!event.breaking && event.builder.buildRequest().block == Blocks.thoriumReactor || event.builder.buildRequest().block == Blocks.combustionGenerator || event.builder.buildRequest().block == Blocks.turbineGenerator || event.builder.buildRequest().block == Blocks.impactReactor && event.builder instanceof Player) {
                        Tile coreTile = ((Player) event.builder).getClosestCore().getTile();
                        if (coreTile == null) {
                            return;
                        }
                        double distance = Utils.DistanceBetween(coreTile.x, coreTile.y, nukeTile.x, nukeTile.y);
                        if (distance <= Utils.nukeDistance) {
                            Call.beginBreak(event.builder.getTeam(), event.tile.x, event.tile.y);
                            Call.onDeconstructFinish(event.tile, Blocks.thoriumReactor, ((Player) event.builder).id);
                            ((Player) event.builder).kill();
                            ((Player) event.builder).sendMessage("[scarlet]Too close to the core, please find a better spot.");
                        }
                    }
                } catch (Exception ignored) {}
            }
        });


        // warning logs

        if (data.has("warnings_chat_channel_id")) {
            TextChannel tc = this.getTextChannel(data.getString("warnings_chat_channel_id"));
            if (tc != null) {
                HashMap<String, String> infoMessageBuffer = new HashMap<>();
                Events.on(EventType.BuildSelectEvent.class, event -> {
                    // we dont want to log all blocks, thats too much.. only suspicious ones
                    if (!event.breaking && event.builder.buildRequest().block == Blocks.thoriumReactor || event.builder.buildRequest().block == Blocks.combustionGenerator || event.builder.buildRequest().block == Blocks.turbineGenerator || event.builder.buildRequest().block == Blocks.impactReactor && event.builder instanceof Player) {
                        Player builder = (Player) event.builder;
                        Block buildBlock = event.builder.buildRequest().block;
                        Tile buildTile = event.builder.buildRequest().tile();
                        infoMessageBuffer.put(builder.name, "Block: " + buildBlock.name + " Location: (" + buildTile.x + ", " + buildTile.y + ")");

                        if(infoMessageBuffer.size() >= Utils.messageBufferSize) { // if message buffer size is below the expected size
                            EmbedBuilder eb = new EmbedBuilder().setTitle(new SimpleDateFormat("yyyy_MM_dd").format(Calendar.getInstance().getTime()));
                            eb.setColor(Utils.Pals.info);
                            for (Map.Entry<String, String> entry : infoMessageBuffer.entrySet()) {
                                String username = entry.getKey();
                                String message = entry.getValue();
                                eb.addField(Utils.escapeCharacters(username), message);
                            }
                            tc.sendMessage(eb);
                            infoMessageBuffer.clear();
                        }

                    }
                });

                HashMap<String, String> warnMessageBuffer = new HashMap<>();
                Events.on(EventType.TapConfigEvent.class, event -> {
                    if(event.player!= null) {
                        warnMessageBuffer.put(Utils.escapeCharacters(event.player.name), "Block: " + event.tile.block().name +  " Location: (" + event.tile.x + ", " + event.tile.y + ")");

                        if(warnMessageBuffer.size() >= Utils.messageBufferSize) { // if message buffer size is below the expected size
                            EmbedBuilder eb = new EmbedBuilder().setTitle(new SimpleDateFormat("yyyy_MM_dd").format(Calendar.getInstance().getTime()));
                            eb.setColor(Utils.Pals.warning);
                            for (Map.Entry<String, String> entry : warnMessageBuffer.entrySet()) {
                                String username = entry.getKey();
                                String message = entry.getValue();
                                eb.addField(Utils.escapeCharacters(username), message);
                            }
                            tc.sendMessage(eb);
                            warnMessageBuffer.clear();
                        }
                    }
                });

            }
        }

        // player joined
        Events.on(EventType.PlayerJoin.class, event -> {
            Player player = event.player;
            player.name = player.name.replaceAll("<", "").replaceAll(">", "");
            if(database.containsKey(player.uuid)) {
                int rank = database.get(player.uuid);
                switch(rank) {
                    case 1:
                        Call.sendMessage("[sky]vip " + player.name + " joined the server!");
                        player.name = "[orange]<[][sky]vip[][orange]>[] " + player.name;
                        break;
                    case 2:
                        Call.sendMessage("[#fcba03]moderator " + player.name + " joined the server!");
                        player.name = "[orange]<[][#fcba03]mod[][orange]>[] " + player.name;
                        break;
                    case 3:
                        Call.sendMessage("[scarlet]administrator " + player.name + " joined the server!");
                        player.name = "[orange]<[][scarlet]admin[][orange]>[] " + player.name;
                        break;
                    case 4:
                        Call.sendMessage("[orange]<[][white]io[][orange]>[]" + player.name + " joined the server!");
                        player.name = "[orange]<[][white]io[][orange]>[] " + player.name;
                        break;
                }
            }
        });
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

                if (!data.has("reportschannel_id")) {
                    player.sendMessage("[scarlet]This command is disabled.");
                } else {
                    TextChannel tc = this.getTextChannel(data.getString("dchannel_id"));
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