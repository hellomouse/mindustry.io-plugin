package mindustry.plugin;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

import arc.struct.Array;
import arc.util.Timer;
import arc.util.Timer.Task;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.entities.type.BaseUnit;
import mindustry.world.Build;
import mindustry.world.Tile;
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

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import static mindustry.Vars.*;
import static mindustry.plugin.Utils.*;

public class ioMain extends Plugin {
    public static DiscordApi api = null;
    public static String prefix = ".";
    public static String serverName = "<untitled>";
    public static HashMap<String, PlayerData> database  = new HashMap<>(); // uuid, rank
    public static HashMap<String, Boolean> verifiedIPs = new HashMap<>(); // uuid, verified?
    public static HashMap<String, TempPlayerData> tempPlayerDatas = new HashMap<>(); // uuid, data
    public static Boolean intermission = false;
    private final String fileNotFoundErrorMessage = "File not found: config\\mods\\settings.json";
    private JSONObject alldata;
    public static JSONObject data; //token, channel_id, role_id
    public static String apiKey = "";
    public static boolean rejectUsidMismatch = true;

    //register event handlers and create variables in the constructor
    public ioMain() {
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

        // setup ipdatabase
        try {
            File toRead2 = new File("ipdb.io");
            if(toRead2.length() > 0) {
                FileInputStream fis2 = new FileInputStream(toRead2);
                ObjectInputStream ois2 = new ObjectInputStream(fis2);

                verifiedIPs = (HashMap<String, Boolean>) ois2.readObject();

                ois2.close();
                fis2.close();

                Log.info("discordplugin: ip database loaded successfully");
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

        if(data.has("api_key")){
            apiKey = data.getString("api_key");
            Log.info("apiKey set successfully");
        }

        // player joined
        Events.on(EventType.PlayerJoin.class, event -> {
            Player player = event.player;
            Thread verThread = new Thread(() -> {
                if(verification) {
                    if (verifiedIPs.containsKey(player.uuid)) {
                        Boolean verified = verifiedIPs.get(player.uuid);
                        if (!verified) {
                            Log.info("Unverified player joined: " + player.name);
                            Call.onInfoMessage(player.con, verificationMessage);
                        }
                    } else {
                        String url = "http://api.vpnblocker.net/v2/json/" + player.con.address + "/" + apiKey;
                        String pjson = ClientBuilder.newClient().target(url).request().accept(MediaType.APPLICATION_JSON).get(String.class);

                        JSONObject json = new JSONObject(new JSONTokener(pjson));
                        boolean cont = true;

                        if (!json.has("host-ip")) cont = false;
                        if (cont) {
                            if (json.getBoolean("host-ip")) { // verification failed
                                Log.info("IP verification failed for: " + player.name);
                                verifiedIPs.put(player.uuid, false);
                                Call.onInfoMessage(player.con, verificationMessage);
                                if (data.has("warnings_chat_channel_id")) {
                                    TextChannel tc = getTextChannel(data.getString("warnings_chat_channel_id"));
                                    if (tc != null) {
                                        EmbedBuilder eb = new EmbedBuilder().setTitle("IP verification failure: " + serverName);
                                        eb.addField("IP", player.con.address);
                                        eb.addField("Username", escapeCharacters(player.name));
                                        eb.addField("UUID", player.uuid);
                                        eb.setColor(Pals.info);
                                        tc.sendMessage(eb);
                                    }
                                }
                            } else {
                                Log.info("IP verification success for: " + player.name);
                                verifiedIPs.put(player.uuid, true); // verification successful
                            }
                        }
                    }
                }
            });
            verThread.start();

            // reset all previous tags
            player.name = player.name.replaceFirst("<(.*)>", "");
            player.name = player.name.replaceAll("<", "");
            player.name = player.name.replaceAll(">", "");

            TempPlayerData tempData = new TempPlayerData(player);
            tempPlayerDatas.put(player.uuid, tempData);

            if(database.containsKey(player.uuid)) {
                PlayerData data = database.get(player.uuid);
                int rank = data.getRank();
                if (data.usid == null) {
                    data.usid = player.usid;
                } else {
                    if (!data.usid.equals(player.usid)) {
                        if (rejectUsidMismatch) {
                            player.con.kick("USID mismatch. Please join http://discord.mindustry.io and ask for a moderator.");
                        } else {
                            Call.onInfoMessage(player.con, "USID mismatch. Please join http://discord.mindustry.io and ask for a moderator.\nProgress towards the active rank will not be counted and you will not be able to use commands.");
                        }
                        return;
                    }
                }
                if (rank > 0) {
                    // trusted players should have higher limits
                    tempData.configureRatelimit.eventLimit *= 2;
                    tempData.rotateRatelimit.eventLimit *= 2;
                }
                switch (rank) { // apply new tag
                    case 1:
                        Call.sendMessage("[sky]active player " + player.name + " joined the server!");
                        player.name = "[sky]<active>[] " + player.name;
                        break;
                    case 2:
                        Call.sendMessage("[#fcba03]regular player " + player.name + " joined the server!");
                        player.name = "[#fcba03]<regular>[] " + player.name;
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
                tempData.origName = player.name;
            } else { // not in database
                database.put(player.uuid, new PlayerData(player.usid, 0));
            }

            if (welcomeMessage.length() > 0) {
                Call.onInfoMessage(player.con, formatMessage(player, welcomeMessage));
            }
        });

        // player built building
        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (event.player == null) return;
            if (event.breaking) return;
            PlayerData data = getData(event.player);
            if (data == null) return;
            if (event.tile.block() != null) {
                if (!activeRequirements.bannedBlocks.contains(event.tile.block())) {
                    data.incrementBuilding(1);
                }
            }
        });

        Events.on(EventType.GameOverEvent.class, event -> {
            for (Player p : playerGroup.all()) {
                PlayerData data = getData(p);
                if (data != null) {
                    data.incrementGames();
                    Call.onInfoToast(p.con, "[scarlet]+1 games played", 9);
                }
            }
            intermission = true;
        });

        Events.on(EventType.WorldLoadEvent.class, event -> {
            MapRules.run();
            intermission = false;
            for(Player p : playerGroup.all()) {
                Call.onInfoMessage(p.con, formatMessage(p, welcomeMessage));

                TempPlayerData tdata = tempPlayerDatas.get(p.uuid);
                if (tdata != null) {
                    tdata.spawnedPowerGen = false;
                    tdata.spawnedLichPet = false;
                    tdata.draugPets.clear();
                }
            }
        });

        Events.on(EventType.PlayerLeave.class, event -> {
            tempPlayerDatas.remove(event.player.uuid);
        });

        Core.app.post(this::loop);
    }

    // run things here instead of in threads please
    public void loop() {
        for (Entry<String, TempPlayerData> entry : tempPlayerDatas.entrySet()) {
            TempPlayerData tdata = entry.getValue();
            Player p = tdata.playerRef.get();
            if (p == null) { // player gone
                tempPlayerDatas.remove(entry.getKey());
                continue;
            }

            // update rainbows
            String playerNameUnmodified = tdata.origName;
            Integer hue = tdata.hue;
            if (hue < 360) {
                hue = hue + 1;
            } else {
                hue = 0;
            }

            String hex = "#" + Integer.toHexString(Color.getHSBColor(hue / 360f, 1f, 1f).getRGB()).substring(2);
            String[] c = playerNameUnmodified.split(" ", 2);
            p.name = c[0] + " [" + hex + "]" + escapeColorCodes(c[1]);
            tdata.setHue(hue);

            // update pets
            for (BaseUnit unit : tdata.draugPets) if (!unit.isAdded()) tdata.draugPets.remove(unit);
        }

        Core.app.post(this::loop);
    }

    public static PlayerData getData(Player p) {
        PlayerData data = database.get(p.uuid);
        if (data == null) return null;
        if (!p.usid.equals(data.usid)) return null;
        return data;
    }

    public static int getRank(Player p) {
        PlayerData data = getData(p);
        if (data == null) return 0;
        return data.getRank();
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

            handler.<Player>register("rainbow", "[regular+] Give your username a rainbow animation", (args, player) -> {
                if (getRank(player) >= 2) {
                    TempPlayerData tdata = tempPlayerDatas.get(player.uuid);
                    if (tdata == null) return; // shouldn't happen, ever
                    if (tdata.doRainbow) {
                        player.sendMessage("[sky]Rainbow effect toggled off.");
                        tdata.doRainbow = false;
                    } else {
                        player.sendMessage("[sky]Rainbow effect toggled on.");
                        tdata.doRainbow = true;
                    }
                } else {
                    player.sendMessage(noPermissionMessage);
                }
            });

            handler.<Player>register("draugpet", "[active+] Spawn a draug mining drone for your team (disabled on pvp)", (args, player) -> {
                if(!state.rules.pvp || player.isAdmin) {
                    int rank = getRank(player);
                    if (rank >= 1) {
                        TempPlayerData tdata = tempPlayerDatas.get(player.uuid);
                        if (tdata == null) return; // should never happen
                        if (tdata.draugPets.size < rank || player.isAdmin) {
                            BaseUnit baseUnit = UnitTypes.draug.create(player.getTeam());
                            baseUnit.set(player.getX(), player.getY());
                            baseUnit.add();
                            tdata.draugPets.add(baseUnit);
                            Call.sendMessage(player.name + "[#b177fc] spawned in a draug pet! " + tdata.draugPets.size + "/" + rank + " spawned.");
                        }
                    } else {
                        player.sendMessage(noPermissionMessage);
                    }
                } else {
                    player.sendMessage(noPermissionMessage);
                }
            });

            handler.<Player>register("lichpet", "[donator+] Spawn yourself a lich defense pet (max. 1 per game, lasts 2 minutes, disabled on pvp)", (args, player) -> {
                if(!state.rules.pvp || player.isAdmin) {
                    if (getRank(player) >= 3) {
                        TempPlayerData tdata = tempPlayerDatas.get(player.uuid);
                        if (tdata == null) return;
                        if (!tdata.spawnedLichPet || player.isAdmin) {
                            tdata.spawnedLichPet = true;
                            BaseUnit baseUnit = UnitTypes.lich.create(player.getTeam());
                            baseUnit.set(player.getClosestCore().x, player.getClosestCore().y);
                            baseUnit.health = 200f;
                            baseUnit.add();
                            Call.sendMessage(player.name + "[#ff0000] spawned in a lich defense pet! (lasts 2 minutes)");
                            Timer.schedule(baseUnit::kill, 120);
                        } else {
                            player.sendMessage("[#42a1f5]You already spawned a lich defense pet in this game!");
                        }
                    } else {
                        player.sendMessage(noPermissionMessage);
                    }
                } else {
                    player.sendMessage(noPermissionMessage);
                }
            });

            handler.<Player>register("powergen", "[donator+] Spawn yourself a power generator.", (args, player) -> {
                if(!state.rules.pvp || player.isAdmin) {
                    if (getRank(player) >= 3) {
                        TempPlayerData tdata = tempPlayerDatas.get(player.uuid);
                        if (tdata == null) return;
                        if (!tdata.spawnedPowerGen || player.isAdmin) {
                            tdata.spawnedPowerGen = true;

                            float x = player.getX();
                            float y = player.getY();

                            Tile targetTile = world.tileWorld(x, y);
                            if (targetTile == null) {
                                player.sendMessage("[scarlet]Invalid tile");
                                return;
                            }

                            if (Build.validPlace(player.getTeam(), targetTile.x, targetTile.y, Blocks.rtgGenerator, 0)) {
                                player.sendMessage("[scarlet]Cannot place a power generator here");
                                return;
                            }

                            targetTile.setNet(Blocks.rtgGenerator, player.getTeam(), 0);
                            Call.sendMessage(player.name + "[#ff82d1] spawned in a power generator!");

                            // ok seriously why is this necessary
                            new Object() {
                                private Task task;
                                {
                                    task = Timer.schedule(() -> {
                                        if (targetTile.block() == Blocks.rtgGenerator) {
                                            Call.transferItemTo(Items.thorium, 1, targetTile.drawx(), targetTile.drawy(), targetTile);
                                        } else {
                                            player.sendMessage("[scarlet]Your power generator was destroyed!");
                                            task.cancel();
                                        }
                                    }, 0, 6);
                                }
                            };
                        } else {
                            player.sendMessage("[#ff82d1]You already spawned a power generator in this game!");
                        }
                    } else {
                        player.sendMessage(noPermissionMessage);
                    }
                } else {
                    player.sendMessage("[scarlet] This command is disabled on pvp.");
                }
            });

            handler.<Player>register("spawn", "[active+] Skip the core spawning stage and spawn instantly.", (args, player) -> {
                if(!state.rules.pvp || player.isAdmin) {
                    if (database.containsKey(player.uuid)) {
                        if (database.get(player.uuid).getRank() >= 1) {
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

            handler.<Player>register("buildpower", "[donator+] Increase your build power 5x, making you build almost instantly.", (args, player) -> {
                if(!state.rules.pvp || player.isAdmin) {
                    if (database.containsKey(player.uuid)) {
                        if (database.get(player.uuid).getRank() >= 3) {
                            player.mech.buildPower = 5f;
                            player.sendMessage("Buildpower applied!");
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

            handler.<Player>register("verify", "<playerid/playername>", "<mod+> Verify the specified player and allow them to build.", (args, player) -> {
                if(args[0].length() > 0) {
                    if (database.containsKey(player.uuid)) {
                        if (database.get(player.uuid).getRank() >= 4) { // 4 = moderator
                            Player p = findPlayer(args[0]);
                            if (p != null) {
                                if (verifiedIPs.containsKey(p.uuid)) {
                                    verifiedIPs.put(p.uuid, true);
                                    player.sendMessage("[sky]Verified " + p.name + " successfully.");
                                } else {
                                    player.sendMessage("[scarlet]Error: uuid not found in ip database");
                                }
                            } else {
                                player.sendMessage("[scarlet]Error: player not found or offline");
                            }
                        } else {
                            Call.onInfoMessage(noPermissionMessage);
                        }
                    }
                } else {
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