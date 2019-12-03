package io.mindustry.plugin;

import io.mindustry.plugin.discordcommands.Command;
import io.mindustry.plugin.discordcommands.Context;
import io.mindustry.plugin.discordcommands.DiscordCommands;
import io.mindustry.plugin.discordcommands.RoleRestrictedCommand;
import io.anuke.arc.Core;
import io.anuke.arc.Events;
import io.anuke.arc.files.FileHandle;
import io.anuke.arc.collection.Array;

import io.anuke.mindustry.core.GameState;
import io.anuke.mindustry.entities.type.Player;
import io.anuke.mindustry.game.EventType.GameOverEvent;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.maps.Map;
import io.anuke.mindustry.maps.Maps;
import io.anuke.mindustry.io.SaveIO;
import io.anuke.mindustry.net.Administration;

import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageAttachment;

import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.InflaterInputStream;

import static io.anuke.mindustry.Vars.*;

public class ServerCommands {

    private JSONObject data;

    private final Field mapsListField;

    public ServerCommands(JSONObject data){
        this.data = data;

        Class<Maps> mapsClass = Maps.class;
        Field mapsField;
        try {
            mapsField = mapsClass.getDeclaredField("maps");
        } catch (NoSuchFieldException ex) {
            throw new RuntimeException("Could not find field 'maps' of class 'io.anuke.mindustry.maps.Maps'");
        }
        mapsField.setAccessible(true);
        this.mapsListField = mapsField;
    }

    public void registerCommands(DiscordCommands handler) {
        if (data.has("gameOver_role_id")) {
            handler.registerCommand(new RoleRestrictedCommand("gameover") {
                {
                    help = "Force a game over.";
                    role = data.getString("gameOver_role_id");
                }
                public void run(Context ctx) {
                    if (state.is(GameState.State.menu)) {
                        ctx.reply("Invalid state");
                        return;
                    }
                    Events.fire(new GameOverEvent(Team.crux));
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("Command executed.")
                            .setDescription("Done. New game starting in 10 seconds.");
                    ctx.channel.sendMessage(eb);
                }
            });
        }
        handler.registerCommand(new Command("maps") {
            {
                help = "Check a list of available maps and their ids.";
            }
            public void run(Context ctx) {
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("All available maps in the playlist:");
                Array<Map> mapList = maps.customMaps();
                for (int i = 0; i < mapList.size; i++) {
                    Map m = mapList.get(i);
                    eb.addInlineField(String.valueOf(i), m.name() + " `" + m.width + " x " + m.height + "`");
                }
                ctx.channel.sendMessage(eb);
            }
        });
        if (data.has("changeMap_role_id")) {
            handler.registerCommand(new RoleRestrictedCommand("changemap"){
                {
                    help = "<mapname/mapid> Change the current map to the one provided.";
                    role = data.getString("changeMap_role_id");
                }

                @SuppressWarnings("unchecked")
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    if (ctx.args.length < 2) {
                        eb.setTitle("Command terminated.");
                        eb.setDescription("Not enough arguments, use `.changemap <mapname|mapid>`");
                        ctx.channel.sendMessage(eb);
                        return;
                    }
                    Map found = Utils.getMapBySelector(ctx.message.trim());
                    if (found == null) {
                        eb.setTitle("Command terminated.");
                        eb.setDescription("Map \"" + Utils.escapeBackticks(ctx.message.trim()) + "\" not found!");
                        ctx.channel.sendMessage(eb);
                        return;
                    }

                    Array<Map> mapsList;
                    try {
                        mapsList = (Array<Map>)mapsListField.get(maps);
                    } catch (IllegalAccessException ex) {
                        throw new RuntimeException("unreachable");
                    }

                    Map targetMap = found;
                    Array<Map> tempMapsList = mapsList.removeAll(map -> !map.custom || map != targetMap);

                    try {
                        mapsListField.set(maps, tempMapsList);
                    } catch (IllegalAccessException ex) {
                        throw new RuntimeException("unreachable");
                    }

                    Events.fire(new GameOverEvent(Team.crux));

                    try {
                        mapsListField.set(maps, mapsList);
                    } catch (IllegalAccessException ex) {
                        throw new RuntimeException("unreachable");
                    }

                    eb.setTitle("Command executed.");
                    eb.setDescription("Changed map to " + targetMap.name());
                    ctx.channel.sendMessage(eb);

                    maps.reload();
                }
            });
        }
        if (data.has("closeServer_role_id")) {
            handler.registerCommand(new RoleRestrictedCommand("exit") {
                {
                    help = "Close the server.";
                    role = data.getString("closeServer_role_id");
                }
                public void run(Context ctx) {
                    net.dispose();
                    Core.app.exit();
                }
            });
        }
        if (data.has("banPlayers_role_id")) {
            String banRole = data.getString("banPlayers_role_id");
            handler.registerCommand(new RoleRestrictedCommand("ban") {
                {
                    help = "<ip/id> <ip/id> Ban a player by the provided ip or id.";
                    role = banRole;
                }
 
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTimestampToNow();
                    String target = ctx.args[1];
                    int id = -1;
                    try {
                        id = Integer.parseInt(target);
                    } catch (NumberFormatException ex) {}
                    if (target.length() > 0) {
                        for (Player p : playerGroup.all()) {
                            if (p.con.address.equals(target) || p.id == id) {
                                netServer.admins.banPlayer(p.uuid);
                                eb.setTitle("Command executed.");
                                eb.setDescription("Banned " + p.name + "(#" + p.id + ") `" + p.con.address + "` successfully!");
                                ctx.channel.sendMessage(eb);
                                Call.onKick(p.con, "You've been banned by: " + ctx.author.getName() + ". Appeal at http://discord.mindustry.io");
                                Call.sendChatMessage("[scarlet]" + Utils.escapeBackticks(p.name) + " has been banned.");
                                //Utils.LogAction("ban", "Remotely executed ban command", ctx.author, p.name + " : " + p.con.address);
                            }
                        }
                    } else {
                        eb.setTitle("Command terminated");
                        eb.setDescription("Not enough arguments / usage: `ban <id|ip>`");
                        ctx.channel.sendMessage(eb);
                    }
                }
            });
            handler.registerCommand(new RoleRestrictedCommand("unban") {
                EmbedBuilder eb = new EmbedBuilder();
                {
                    help = "Unban a player by the provided ip.";
                    role = banRole;
                }
                public void run(Context ctx) {
                    String ip;
                    if(ctx.args.length==2){ ip = ctx.args[1]; } else {ctx.reply("Invalid arguments provided, use the following format: .unban <ip>"); return;}

                    if (netServer.admins.unbanPlayerIP(ip)) {
                        eb.setTitle("Command executed.");
                        eb.setDescription("Unbanned `" + ip + "` successfully");
                        ctx.channel.sendMessage(eb);
                    } else {
                        eb.setTitle("Command terminated.");
                        eb.setDescription("No such ban exists.");
                        ctx.channel.sendMessage(eb);
                    }
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("motd") {
                {
                    help = "Change the default join message";
                    role = data.getString("gameOver_role_id");
                }
                public void run(Context ctx) {
                    String message;
                    String oldMotd = Utils.welcomeMessage;
                    if(ctx.args.length==2){ message = ctx.args[1]; } else {ctx.reply("Invalid arguments provided, use the following format: .motd <text>"); return;}
                    Utils.welcomeMessage = message;
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("Command executed.")
                            .setDescription("Changed **MOTD** from \"" + oldMotd + "\" -> " + message);
                    ctx.channel.sendMessage(eb);
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("bans") {
                {
                    help = "Get info about all banned players.";
                    role = banRole;
                }
                public void run(Context ctx) {
                    List<String> result = new ArrayList<>();
                    Array<Administration.PlayerInfo> bans = netServer.admins.getBanned();
                    for (Administration.PlayerInfo playerInfo : bans) {
                        result.add("\n\n * Last seen IP: " + playerInfo.lastIP);
                        result.add("   All IPs:");
                        for (String ip : playerInfo.ips) result.add("    * " + ip);
                        result.add("   All names:");
                        for (String name : playerInfo.names) result.add("    * " + Utils.escapeBackticks(name));
                    }

                    File f = new File(new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(Calendar.getInstance().getTime()) + "-IO_BANS.txt");
                    try {
                        FileWriter fw;
                        fw = new FileWriter(f.getAbsoluteFile());
                        BufferedWriter bw = new BufferedWriter(fw);
                        bw.write(Utils.constructMessage(result));
                        bw.close(); // Be sure to close BufferedWriter
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    ctx.channel.sendMessage(f);
                    f.delete();
                }
            });
        }
        if (data.has("kickPlayers_role_id")) {
            handler.registerCommand(new RoleRestrictedCommand("kick") {
                EmbedBuilder eb = new EmbedBuilder()
                        .setTimestampToNow();
                {
                    help = "<ip/id> Kick a player by the provided ip or id.";
                    role = data.getString("kickPlayers_role_id");
                }
                public void run(Context ctx) {
                    String target;
                    if(ctx.args.length==2){ target = ctx.args[1]; } else {ctx.reply("Invalid arguments provided, use the following format: .kick <ip/id>"); return;}

                    int id = -1;
                    try {
                        id = Integer.parseInt(target);
                    } catch (NumberFormatException ex) {}
                    if (target.length() > 0) {
                        for (Player p : playerGroup.all()) {
                            if (p.con.address.equals(target) || p.id == id) {
                                eb.setTitle("Command executed.");
                                eb.setDescription("Kicked " + p.name + "(#" + p.id + ") `" + p.con.address + "` successfully.");
                                Call.sendChatMessage("[scarlet]" + Utils.escapeBackticks(p.name) + " has been kicked.");
                                Call.onKick(p.con, "You've been kicked by: " + ctx.author.getName());
                                ctx.channel.sendMessage(eb);
                                //Utils.LogAction("kick", "Remotely executed kick command", ctx.author, p.name + " : " + p.con.address);
                            }
                        }
                    } else {
                        eb.setTitle("Command terminated.");
                        eb.setDescription("Not enough arguments / usage: `kick <ip/id>`");
                        ctx.channel.sendMessage(eb);
                    }
                }
            });
            handler.registerCommand(new RoleRestrictedCommand("antinuke") {
                {
                    help = "<on/off/> Toggle the antinuke option, or check its status.";
                    role = data.getString("kickPlayers_role_id");
                }
                public void run(Context ctx) {
                    if (ctx.args.length > 1) {
                        String toggle = ctx.args[1].toLowerCase();
                        if (toggle.equals("on")) {
                            Utils.antiNukeEnabled = true;
                            ctx.reply("Anti nuke was enabled.");
                        } else if (toggle.equals("off")) {
                            Utils.antiNukeEnabled = false;
                            ctx.reply("Anti nuke was disabled.");
                        } else {
                            ctx.reply("Usage: antinuke <on/off>");
                        }
                    } else {
                        EmbedBuilder eb = new EmbedBuilder();
                        if (Utils.antiNukeEnabled) {
                            eb.setTitle("Enabled");
                            eb.setDescription("Anti nuke is currently enabled. Use `.antinuke off` to disable it.");
                            ctx.channel.sendMessage(eb);
                        } else {
                            eb.setTitle("Disabled");
                            eb.setDescription("Anti nuke is currently disabled. Use `.antinuke on` to enable it.");
                            ctx.channel.sendMessage(eb);
                        }
                    }
                }
            });
        }

        if (data.has("spyPlayers_role_id")) {
            handler.registerCommand(new RoleRestrictedCommand("playersinfo") {
                {
                    help = "Check the information about all players on the server.";
                    role = data.getString("spyPlayers_role_id");
                }
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("Players online: " + playerGroup.size());
                    for (Player p : playerGroup.all()) {
                        String p_ip = p.con.address;
                        String p_name = p.name;
                        if (netServer.admins.isAdmin(p.uuid, p.usid)) {  // make admins special :)
                            p_ip = "*hidden*";
                            p_name = "**" + p_name + "**";
                        }
                        eb.addInlineField(Utils.escapeBackticks(p_name),  p_ip + " : #" + p.id);
                    }
                    ctx.channel.sendMessage(eb);
                }
            });
        }
        if (data.has("mapConfig_role_id")) {
            String mapConfigRole = data.getString("mapConfig_role_id");
            handler.registerCommand(new RoleRestrictedCommand("uploadmap") {
                {
                    help = "<.msav attachment> Upload a new map (Include a .msav file with command message)";
                    role = mapConfigRole;
                }
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    Array<MessageAttachment> ml = new Array<>();
                    for (MessageAttachment ma : ctx.event.getMessageAttachments()) {
                        if (ma.getFileName().split("\\.", 2)[1].trim().equals("msav")) {
                            ml.add(ma);
                        }
                    }
                    if (ml.size != 1) {
                        eb.setTitle("Map upload terminated.");
                        eb.setDescription("You need to add one valid .msav file!");
                        ctx.channel.sendMessage(eb);
                        return;
                    } else if (Core.settings.getDataDirectory().child("maps").child(ml.get(0).getFileName()).exists()) {
                        eb.setTitle("Map upload terminated.");
                        eb.setDescription("There is already a map with this name on the server!");
                        ctx.channel.sendMessage(eb);
                        return;
                    }
                    // more custom filename checks possible

                    CompletableFuture<byte[]> cf = ml.get(0).downloadAsByteArray();
                    FileHandle fh = Core.settings.getDataDirectory().child("maps").child(ml.get(0).getFileName());

                    try {
                        byte[] data = cf.get();
                        if (!SaveIO.isSaveValid(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data))))) {
                            eb.setTitle("Map upload terminated.");
                            eb.setDescription("Map file corrupted or invalid.");
                            ctx.channel.sendMessage(eb);
                            return;
                        }
                        fh.writeBytes(cf.get(), false);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    maps.reload();
                    eb.setTitle("Map upload completed.");
                    eb.setDescription(ml.get(0).getFileName() + " was added succesfully into the playlist!");
                    ctx.channel.sendMessage(eb);
                    //Utils.LogAction("uploadmap", "Uploaded a new map", ctx.author, null);
                }
            });
            handler.registerCommand(new RoleRestrictedCommand("removemap") {
                {
                    help = "<mapname/mapid> Remove a map from the playlist (use mapname/mapid retrieved from the .maps command)";
                    role = mapConfigRole;
                }
                @Override
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    if (ctx.args.length < 2) {
                        eb.setTitle("Command terminated.");
                        eb.setDescription("Not enough arguments, use `removemap <mapname/mapid>`");
                        ctx.channel.sendMessage(eb);
                        return;
                    }
                    Map found = Utils.getMapBySelector(ctx.message.trim());
                    if (found == null) {
                        eb.setTitle("Command terminated.");
                        eb.setDescription("Map not found");
                        ctx.channel.sendMessage(eb);
                        return;
                    }

                    maps.removeMap(found);
                    maps.reload();
                    
                    eb.setTitle("Command executed.");
                    eb.setDescription(found.name() + " was successfully removed from the playlist.");
                    ctx.channel.sendMessage(eb);
                }
            });
        }
        if(IoPlugin.data.has("mapSubmissions_channel_id")){
            TextChannel tc = IoPlugin.getTextChannel(IoPlugin.data.getString("mapSubmissions_channel_id"));
            handler.registerCommand(new Command("submitmap") {
                {
                    help = "<.msav attachment> Submit a new map to be added into the server playlist in a .msav file format.";
                }
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    Array<MessageAttachment> ml = new Array<>();
                    for (MessageAttachment ma : ctx.event.getMessageAttachments()) {
                        if (ma.getFileName().split("\\.", 2)[1].trim().equals("msav")) {
                            ml.add(ma);
                        }
                    }
                    if (ml.size != 1) {
                        eb.setTitle("Map upload terminated.");
                        eb.setDescription("You need to add one valid .msav file!");
                        ctx.channel.sendMessage(eb);
                        return;
                    } else if (Core.settings.getDataDirectory().child("maps").child(ml.get(0).getFileName()).exists()) {
                        eb.setTitle("Map upload terminated.");
                        eb.setDescription("There is already a map with this name on the server!");
                        ctx.channel.sendMessage(eb);
                        return;
                    }
                    CompletableFuture<byte[]> cf = ml.get(0).downloadAsByteArray();

                    try {
                        byte[] data = cf.get();
                        if (!SaveIO.isSaveValid(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data))))) {
                            eb.setTitle("Map upload terminated.");
                            eb.setDescription("Map file corrupted or invalid.");
                            ctx.channel.sendMessage(eb);
                            return;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    eb.setTitle("Map upload completed.");
                    eb.setDescription(ml.get(0).getFileName() + " was successfully queued for review by moderators!");
                    ctx.channel.sendMessage(eb);
                    EmbedBuilder eb2 = new EmbedBuilder()
                            .setTitle("A map submission has been made.")
                            .setAuthor(ctx.author)
                            .setTimestampToNow()
                            .setDescription(ml.get(0).getFileName());
                    File mapFile = new File(ml.get(0).getFileName());
                    CompletableFuture<byte[]> fileContent = ml.get(0).downloadAsByteArray();
                    // TODO: make it post the map submission in tc
                }
            });
        }
    }
}
