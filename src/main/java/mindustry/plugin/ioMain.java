package mindustry.plugin;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.text.SimpleDateFormat;
import java.util.*;

import arc.struct.Array;
import mindustry.content.UnitTypes;
import mindustry.entities.type.BaseUnit;
import mindustry.entities.type.EffectEntity;
import mindustry.entities.type.base.BuilderDrone;
import mindustry.entities.units.UnitState;
import mindustry.net.Administration;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.json.JSONObject;
import org.json.JSONTokener;

import arc.Core;
import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import mindustry.Vars;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import mindustry.gen.Call;

import static mindustry.Vars.*;
import static mindustry.Vars.player;
import static mindustry.plugin.Utils.*;

public class ioMain extends Plugin {
    public static DiscordApi api = null;
    public static String prefix = ".";
    public static String serverName = "<untitled>";
    public static HashMap<String, PlayerData> database  = new HashMap<String, PlayerData>(); // uuid, rank
    public static Array<String> rainbowedPlayers = new Array<>(); // player
    public static Array<String> spawnedLichPet = new Array<>();
    public static HashMap<String, TempPlayerData> tempPlayerDatas = new HashMap<>(); // uuid, data
    public static HashMap<String, Integer> spawnedDraugPets = new HashMap<>(); // player, amount of draugs spawned
    public static Boolean intermission = false;
    private final String fileNotFoundErrorMessage = "File not found: config\\mods\\settings.json";
    private JSONObject alldata;
    public static JSONObject data; //token, channel_id, role_id


    //register event handlers and create variables in the constructor
    public ioMain() throws InterruptedException {
        Utils.init();

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

                database = (HashMap<String, PlayerData>) ois.readObject();

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



        // live chat
        if (data.has("live_chat_channel_id")) {
            TextChannel tc = getTextChannel(data.getString("live_chat_channel_id"));
            if (tc != null) {
                HashMap<String, String> messageBuffer = new HashMap<>();
                Events.on(EventType.PlayerChatEvent.class, event -> {
                    messageBuffer.put(escapeCharacters(event.player.name), escapeCharacters(event.message));
                    if(messageBuffer.size() >= messageBufferSize) { // if message buffer size is below the expected size
                        EmbedBuilder eb = new EmbedBuilder().setTitle(new SimpleDateFormat("yyyy_MM_dd").format(Calendar.getInstance().getTime()));
                        for (Map.Entry<String, String> entry : messageBuffer.entrySet()) {
                            String username = entry.getKey();
                            String message = entry.getValue();
                            eb.addField(escapeCharacters(username), escapeCharacters(message));
                        }
                        tc.sendMessage(eb);
                        messageBuffer.clear();
                    }
                });
            }
        }


        // warning logs

        if (data.has("warnings_chat_channel_id")) {
            TextChannel tc = getTextChannel(data.getString("warnings_chat_channel_id"));
            if (tc != null) {
                HashMap<String, String> warnMessageBuffer = new HashMap<>();
                Events.on(EventType.TapConfigEvent.class, event -> {
                    if(event.player != null) {

                        warnMessageBuffer.put("Block: " + event.tile.block().name +  " Location: (" + event.tile.x + ", " + event.tile.y + ") Configuration: " + event.value, event.player.name);

                        if(warnMessageBuffer.size() >= messageBufferSize) { // if message buffer size is below the expected size
                            EmbedBuilder eb = new EmbedBuilder().setTitle("Logs from " + new SimpleDateFormat("yyyy_MM_dd").format(Calendar.getInstance().getTime()));
                            eb.setColor(Pals.warning);
                            eb.setTimestampToNow();
                            for (Map.Entry<String, String> entry : warnMessageBuffer.entrySet()) {
                                String message = entry.getKey();
                                String username = entry.getValue();
                                eb.addField(escapeCharacters(username), message);
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

            if(database.containsKey(player.uuid)) {
                int rank = database.get(player.uuid).getRank();
                if(rank==0) {
                    player.name = player.name.replaceFirst("\\<(.*)\\>", "");
                    player.name = player.name.replaceAll("<", "");
                    player.name = player.name.replaceAll(">", "");
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
                        Call.sendMessage("[#fcba03]regulat player " + player.name + " joined the server!");
                        player.name = "[#fcba03]<vip>[] " + player.name;
                        break;
                    case 3:
                        Call.sendMessage("[scarlet]donator " + player.name + " joined the server!");
                        player.name = "[scarlet]<donator>[] " + player.name;
                        break;
                    case 4:
                        Call.sendMessage("[orange]<[][white]io moderator[][orange]>[] " + player.name + " joined the server!");
                        player.name = "[white]<mod>[] " + player.name;
                        break;
                    case 5:
                        Call.sendMessage("[orange]<[][white]io admin[][orange]>[] " + player.name + " joined the server!");
                        player.name = "[white]<admin>[] " + player.name;
                        break;
                }
            } else { // not in database
                database.put(player.uuid, new PlayerData(0));

                player.name = player.name.replaceAll("<", "");
                player.name = player.name.replaceAll(">", "");
            }

            if(welcomeMessage.length() > 0){
                Call.onInfoMessage(player.con, formatMessage(player, welcomeMessage));
            }
        });

        // player built building
        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if(event.player!= null){
                if (!event.breaking) {
                    if(database.containsKey(event.player.uuid)) {
                        if(event.tile.block()!=null) {
                            if(!activeRequirements.bannedBlocks.contains(event.tile.block())) {
                                database.get(event.player.uuid).incrementBuilding(1);
                            }
                        }
                    }
                }
            }
        });

        Events.on(EventType.GameOverEvent.class, event -> {
            for (Player p : playerGroup.all()) {
                if (database.containsKey(p.uuid)) {
                    database.get(p.uuid).incrementGames();
                    Call.onInfoToast(p.con, "[scarlet]+1 games played", 9);
                }
            }
            intermission = true;
        });

        Events.on(EventType.WorldLoadEvent.class, event -> {
            spawnedDraugPets.clear();
            spawnedLichPet.clear();
            MapRules.run();
            intermission = false;
            for(Player p : playerGroup.all()) {
                Call.onInfoMessage(p.con, formatMessage(p, welcomeMessage));
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

                if (!data.has("warnings_chat_channel_id")) {
                    player.sendMessage("[scarlet]This command is disabled.");
                } else {
                    TextChannel tc = getTextChannel(data.getString("warnings_chat_channel_id"));
                    if (tc == null) {
                        player.sendMessage("[scarlet]This command is disabled.");
                        return;
                    }
                    tc.sendMessage(escapeCharacters(player.name) + " *@mindustry* : `" + args[0] + "`");
                    player.sendMessage("[scarlet]Successfully sent message to moderators.");
                }

            });

            handler.<Player>register("players", "Display all players and their ids", (args, player) -> {
                StringBuilder builder = new StringBuilder();
                builder.append("[orange]List of players: \n");
                for (Player p : Vars.playerGroup.all()) {
                    if(p.isAdmin) {
                        builder.append("[scarlet]<ADMIN>[] ");
                    } else{
                        builder.append("[lightgray]");
                    }
                    builder.append(p.name).append("[accent] : ").append(p.id).append("\n");
                }
                player.sendMessage(builder.toString());
            });

            handler.<Player>register("rainbow", "[vip+] Give your username a rainbow animation", (args, player) -> {
                if(database.containsKey(player.uuid)) {
                    if(database.get(player.uuid).getRank() >= 2) {
                        if(rainbowedPlayers.contains(player.uuid)) {
                            player.sendMessage("[sky]Rainbow effect toggled off.");
                            rainbowedPlayers.remove(player.uuid);
                            tempPlayerDatas.remove(player.uuid);
                        } else {
                            player.sendMessage("[sky]Rainbow effect toggled on.");
                            rainbowedPlayers.add(player.uuid);
                            tempPlayerDatas.put(player.uuid, new TempPlayerData(0, player.name));
                            Thread rainbowLoop = new Thread() {
                                public void run() {
                                    while(playerGroup.all().contains(player) && rainbowedPlayers.contains(player.uuid) && !intermission) {
                                        try {

                                            TempPlayerData pdata = tempPlayerDatas.get(player.uuid);
                                            String playerNameUnmodified = pdata.realName;
                                            Integer hue = pdata.hue;
                                            if (hue < 360) {
                                                hue = hue + 1;
                                            } else {
                                                hue = 0;
                                            }

                                            String hex = "#" + Integer.toHexString(Color.getHSBColor(hue / 360f, 1f, 1f).getRGB()).substring(2);
                                            String[] c = playerNameUnmodified.split(" ", 2);
                                            player.name = c[0] + " [" + hex + "]" + escapeColorCodes(c[1]);
                                            pdata.setHue(hue);
                                            tempPlayerDatas.replace(player.uuid, pdata);

                                            Thread.sleep(50);
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            };

                            rainbowLoop.start();

                        }
                    } else {
                        player.sendMessage(noPermissionMessage);
                    }
                } else {
                    player.sendMessage(noPermissionMessage);
                }
            });

            handler.<Player>register("draugpet", "[active+] Spawn a draug mining drone for your team (disabled on pvp)", (args, player) -> {
                if(state.rules.attackMode || state.rules.waves || player.isAdmin) {
                    if (database.containsKey(player.uuid)) {
                        int rank = database.get(player.uuid).getRank();
                        if (rank >= 1) {
                            if (spawnedDraugPets.containsKey(player.uuid)) {
                                if (spawnedDraugPets.get(player.uuid) < rank || player.isAdmin) {
                                    spawnedDraugPets.put(player.uuid, spawnedDraugPets.get(player.uuid) + 1);
                                    Call.sendMessage(player.name + "[#b177fc] spawned in a draug pet! " + spawnedDraugPets.get(player.uuid) + "/" + rank + " spawned.");
                                    BaseUnit baseUnit = UnitTypes.draug.create(player.getTeam());
                                    baseUnit.set(player.getX(), player.getY());
                                    baseUnit.add();
                                }
                            } else {
                                spawnedDraugPets.put(player.uuid, 0);
                                player.sendMessage("[#42a1f5]This command will spawn in a draug mining bot, use it again to proceed.");
                            }
                        } else {
                            player.sendMessage(noPermissionMessage);
                        }
                    } else {
                        player.sendMessage(noPermissionMessage);
                    }
                } else {
                    player.sendMessage("[scarlet] This command is disabled on pvp.");
                }
            });

            handler.<Player>register("lichpet", "[mvp+] Spawn yourself a lich defense pet (max. 1 per game, lasts 2 minutes, disabled on pvp)", (args, player) -> {
                if(state.rules.attackMode || state.rules.waves || player.isAdmin) {
                    if (database.containsKey(player.uuid)) {
                        if (database.get(player.uuid).getRank() >= 3) {
                            if (!spawnedLichPet.contains(player.uuid) || player.isAdmin) {
                                spawnedLichPet.add(player.uuid);
                                BaseUnit baseUnit = UnitTypes.lich.create(player.getTeam());
                                baseUnit.set(player.getClosestCore().x, player.getClosestCore().y);
                                baseUnit.health = 200f;
                                baseUnit.add();
                                Call.sendMessage(player.name + "[#ff0000] spawned in a lich defense pet! (lasts 2 minutes)");
                                Thread lichPetLoop = new Thread() {
                                    public void run() {
                                        try {
                                            Thread.sleep(1000 * 60 * 2); // wait 2 minutes
                                            baseUnit.kill();
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                            baseUnit.kill();
                                        }
                                    }
                                };
                                lichPetLoop.start();
                            } else {
                                player.sendMessage("[#42a1f5]You already spawned a lich defense pet in this game!");
                            }
                        } else {
                            player.sendMessage(noPermissionMessage);
                        }
                    } else {
                        player.sendMessage(noPermissionMessage);
                    }
                } else {
                    player.sendMessage("[scarlet] This command is disabled on pvp.");
                }
            });

            handler.<Player>register("spawn", "[vip+] Skip the core spawning stage and spawn instantly.", (args, player) -> {
                if(state.rules.attackMode || state.rules.waves || player.isAdmin) {
                    if (database.containsKey(player.uuid)) {
                        if (database.get(player.uuid).getRank() >= 2) {
                            player.onRespawn(player.getClosestCore().tile);
                            player.sendMessage("Spawned!");
                        } else {
                            player.sendMessage(noPermissionMessage);
                        }
                    } else {
                        player.sendMessage(noPermissionMessage);
                    }
                } else {
                    player.sendMessage("[scarlet] This command is disabled on pvp.");
                }
            });

            handler.<Player>register("stats", "<playerid/playername>", "Get information (playtime, buildings built, etc.) of the specified user. [get playerid from /players]", (args, player) -> {
                if(args[0].length() > 0) {
                    Player p = findPlayer(args[0]);
                    if(p != null){
                        if(database.containsKey(p.uuid)) {
                            Call.onInfoMessage(player.con, formatMessage(p, statMessage));
                        } else {
                            player.sendMessage("[scarlet]Error: " + p.name + "'s playtime is lower than 60 seconds");
                        }
                    } else {
                        player.sendMessage("[scarlet]Error: player not found or offline");
                    }
                } else {
                    Call.onInfoMessage(player.con, formatMessage(player, statMessage));
                }
            });

            handler.<Player>register("info", "Get information (playtime, buildings built, etc.) about yourself.", (args, player) -> { // self info
                if (database.containsKey(player.uuid)) {
                    Call.onInfoMessage(player.con, formatMessage(player, statMessage));
                }
            });

        }

    }



    public static TextChannel getTextChannel(String id){
        Optional<Channel> dc = api.getChannelById(id);
        if (!dc.isPresent()) {
            Log.err("[ERR!] discordplugin: channel not found! " + id);
            return null;
        }
        Optional<TextChannel> dtc = dc.get().asTextChannel();
        if (!dtc.isPresent()){
            Log.err("[ERR!] discordplugin: textchannel not found! " + id);
            return null;
        }
        return dtc.get();
    }

    public Role getRole(String id){
        Optional<Role> r1 = api.getRoleById(id);
        if (!r1.isPresent()) {
            Log.err("[ERR!] discordplugin: adminrole not found! " + id);
            return null;
        }
        return r1.get();
    }

}