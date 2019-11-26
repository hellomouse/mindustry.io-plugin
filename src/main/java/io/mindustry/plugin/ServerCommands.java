package io.mindustry.plugin;

import io.anuke.arc.util.Log;
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

import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.MessageAttachment;

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
    // private final long minMapChangeTime = 30L; // 30 seconds

    private JSONObject data;
    // private long lastMapChange = 0L;

    private final Field mapsListField;

    public ServerCommands(JSONObject data){
        this.data = data;

        // grab private maps array field
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
                    help = "Force a game over";
                    role = data.getString("gameOver_role_id");
                }
                public void run(Context ctx) {
                    if (state.is(GameState.State.menu)) {
                        ctx.reply("Invalid state");
                        return;
                    }
                    Events.fire(new GameOverEvent(Team.crux));
                    ctx.reply("Done. New game starting in 10 seconds.");
                }
            });
        }
        handler.registerCommand(new Command("maps") {
            {
                help = "Get a list of available maps";
            }
            public void run(Context ctx) {
                List<String> result = new ArrayList<>();
                result.add("List of available maps:");
                Array<Map> mapList = maps.customMaps();
                for (int i = 0; i < mapList.size; i++) {
                    Map m = mapList.get(i);
                    result.add("(" + i + ") " + m.name() + " / " + m.width + " x " + m.height);
                }
                ctx.reply(new MessageBuilder().appendCode("", String.join("\n", result)));
            }
        });
        if (data.has("changeMap_role_id")) {
            handler.registerCommand(new RoleRestrictedCommand("changemap"){
                {
                    help = "Change the current map";
                    role = data.getString("changeMap_role_id");
                }
                // reflective access should be perfectly safe
                @SuppressWarnings("unchecked")
                public void run(Context ctx) {
                    if (ctx.args.length < 2) {
                        ctx.reply("Not enough arguments, use `.changemap <number|name>`");
                        return;
                    }
                    Map found = Utils.getMapBySelector(ctx.message.trim());
                    if (found == null) {
                        ctx.reply("Map not found!");
                        return;
                    }

                    // TODO: use new MapProvider api or something
                    // step 1: grab the current maps list
                    Array<Map> mapsList;
                    try {
                        mapsList = (Array<Map>)mapsListField.get(maps);
                    } catch (IllegalAccessException ex) {
                        throw new RuntimeException("unreachable");
                    }
                    
                    // step 2: create new maps array with only the desired custom map
                    Map targetMap = found;
                    Array<Map> tempMapsList = mapsList.removeAll(map -> !map.custom || map != targetMap);

                    // step 3: replace original array with our array
                    try {
                        mapsListField.set(maps, tempMapsList);
                    } catch (IllegalAccessException ex) {
                        throw new RuntimeException("unreachable");
                    }

                    // step 4: fire game over event
                    Events.fire(new GameOverEvent(Team.crux));

                    // step 5: put back original maps list
                    try {
                        mapsListField.set(maps, mapsList);
                    } catch (IllegalAccessException ex) {
                        throw new RuntimeException("unreachable");
                    }

                    ctx.reply("Changed map to " + targetMap.name());

                    // step 6: reload maps
                    maps.reload();

                    /*
                    FileHandle temp = Core.settings.getDataDirectory().child("maps/temp");
                    temp.mkdirs();
    
                    for (Map m1 : maps.customMaps()) {
                        if (m1.equals(world.getMap())) continue;
                        if (m1.equals(found)) continue;
                        m1.file.moveTo(temp);
                    }
                    //reload all maps from that folder
                    maps.reload();
                    //Call gameover
                    Events.fire(new GameOverEvent(Team.crux));
                    //move maps
                    maps.reload();
                    FileHandle mapsDir = Core.settings.getDataDirectory().child("maps");
                    for (FileHandle fh : temp.list()) {
                        fh.moveTo(mapsDir);
                    }
                    temp.deleteDirectory();
                    maps.reload();
    
                    event.getChannel().sendMessage("Next map selected: " + found.name() + "\nThe current map will change in 10 seconds.");
                    this.lastMapChange = System.currentTimeMillis() / 1000L;
                    */
                }
            });
        }
        if (data.has("closeServer_role_id")) {
            handler.registerCommand(new RoleRestrictedCommand("exit") {
                {
                    help = "Close the server";
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
                    help = "Ban a player by id or ip";
                    role = banRole;
                }
 
                public void run(Context ctx) {
                    String target = ctx.args[1];
                    int id = -1;
                    try {
                        id = Integer.parseInt(target);
                    } catch (NumberFormatException ex) {}
                    if (target.length() > 0) {
                        for (Player p : playerGroup.all()) {
                            if (p.con.address.equals(target) || p.id == id) {
                                netServer.admins.banPlayer(p.uuid);
                                ctx.reply("Banned " + p.name + "(#" + p.id + ") `" + p.con.address + "` successfully!");
                                Call.onKick(p.con, "You've been banned by: " + ctx.author.getName() + ". Appeal at http://discord.mindustry.io");
                                Call.sendChatMessage("[scarlet]" + Utils.escapeBackticks(p.name) + " has been banned.");
                            }
                        }
                    } else {
                        ctx.reply("Not enough arguments / usage: `ban <id|ip>`");
                    }
                }
            });
            handler.registerCommand(new RoleRestrictedCommand("unban") {
                {
                    help = "Unban a player by ip";
                    role = banRole;
                }
                public void run(Context ctx) {
                    String ip;
                    if(ctx.args.length==2){ ip = ctx.args[1]; } else {ctx.reply("Invalid arguments provided, use the following format: .unban <ip>"); return;}

                    if (netServer.admins.unbanPlayerIP(ip)) {
                        ctx.reply("Unbanned `" + ip + "` successfully");
                    } else {
                        ctx.reply("No such ban exists.");
                    }
                }
            });
            handler.registerCommand(new RoleRestrictedCommand("bans") {
                {
                    help = "Get info about all banned players.";
                    role = banRole;
                }
                public void run(Context ctx) {
                    List<String> result = new ArrayList<>();
                    result.add("List of bans:");
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
                {
                    help = "Kick a player by ip or id";
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
                                Call.onKick(p.con, "You've been kicked by: " + ctx.author.getName());
                                ctx.reply("Kicked " + p.name + "(#" + p.id + ") `" + p.con.address + "` successfully.");
                                Call.sendChatMessage("[scarlet]" + Utils.escapeBackticks(p.name) + " has been kicked.");
                            }
                        }
                    } else {
                        ctx.reply("Not enough arguments / usage: `kick <id|ip>`");
                    }
                }
            });
            handler.registerCommand(new RoleRestrictedCommand("antinuke") {
                {
                    help = "Toggle antinuke option";
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
                            ctx.reply("Usage: antinuke <on|off>");
                        }
                    } else {
                        if (Utils.antiNukeEnabled) {
                            ctx.reply("Anti nuke is currently enabled. Use `antinuke off` to disable it.");
                        } else {
                            ctx.reply("Anti nuke is currently disabled. Use `antinuke on` to enable it.");
                        }
                    }
                }
            });
        }

        if (data.has("manageMessages_role_id")) {
            handler.registerCommand(new RoleRestrictedCommand("delete") {
                {
                    help = "Delete X amount of messages in the context channel";
                    role = data.getString("manageMessages_role_id");
                }
                @Override
                public void run(Context ctx) {
                    int amt;
                    if(ctx.args.length==2){ amt = Integer.parseInt(ctx.args[1]); } else {ctx.reply("Invalid arguments provided, use the following format: .delete <amount>"); return;}
                    // TODO: make it delete 'amt' messages
                }
            });
        }

        if (data.has("spyPlayers_role_id")) {
            handler.registerCommand(new RoleRestrictedCommand("playersinfo") {
                {
                    help = "Get information on all players";
                    role = data.getString("spyPlayers_role_id");
                }
                public void run(Context ctx) {
                    List<String> result = new ArrayList<>();
                    result.add("Players: " + playerGroup.size());
                    for (Player p : playerGroup.all()) {
                        String p_ip = p.con.address;
                        if (netServer.admins.isAdmin(p.uuid, p.usid)) p_ip = "*hidden*";
                        result.add(" * " + p.name + " `" + p_ip + "`");
                    }
                    ctx.reply(new MessageBuilder().appendCode("", Utils.escapeBackticks(String.join("\n", result))));
                }
            });
        }
        if (data.has("mapConfig_role_id")) {
            String mapConfigRole = data.getString("mapConfig_role_id");
            handler.registerCommand(new RoleRestrictedCommand("uploadmap") {
                {
                    help = "Upload a new map (include .msav file with command message)";
                    role = mapConfigRole;
                }
                public void run(Context ctx) {
                    Array<MessageAttachment> ml = new Array<>();
                    for (MessageAttachment ma : ctx.event.getMessageAttachments()) {
                        if (ma.getFileName().split("\\.", 2)[1].trim().equals("msav")) {
                            ml.add(ma);
                        }
                    }
                    if (ml.size != 1) {
                        ctx.reply("You need to add one valid .msav file!");
                        return;
                    } else if (Core.settings.getDataDirectory().child("maps").child(ml.get(0).getFileName()).exists()) {
                        ctx.reply("There is already a map with this name on the server!");
                        return;
                    }
                    // more custom filename checks possible

                    CompletableFuture<byte[]> cf = ml.get(0).downloadAsByteArray();
                    FileHandle fh = Core.settings.getDataDirectory().child("maps").child(ml.get(0).getFileName());

                    try {
                        byte[] data = cf.get();
                        if (!SaveIO.isSaveValid(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data))))) {
                            ctx.reply("Corrupted .msav file!");
                            return;
                        }
                        fh.writeBytes(cf.get(), false);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    maps.reload();
                    ctx.reply(ml.get(0).getFileName() + " added succesfully!");
                }
            });
            handler.registerCommand(new RoleRestrictedCommand("removemap") {
                {
                    help = "Remove a map from the playlist (use number|name retrieved from .maps)";
                    role = mapConfigRole;
                }
                @Override
                public void run(Context ctx) {
                    if (ctx.args.length < 2) {
                        ctx.reply("Not enough arguments, use `removemap <number|name>`");
                        return;
                    }
                    Map found = Utils.getMapBySelector(ctx.message.trim());
                    if (found == null) {
                        ctx.reply("Map not found");
                        return;
                    }

                    maps.removeMap(found);
                    maps.reload();
                    ctx.reply("Removed map " + found.name());
                }
            });
        }
    }
}
