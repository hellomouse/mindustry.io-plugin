package mindustry.plugin;

import arc.graphics.Color;
import mindustry.content.Mechs;
import mindustry.plugin.discordcommands.Command;
import mindustry.plugin.discordcommands.Context;
import mindustry.plugin.discordcommands.DiscordCommands;
import mindustry.plugin.discordcommands.RoleRestrictedCommand;
import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.struct.Array;

import mindustry.core.GameState;
import mindustry.entities.type.Player;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.maps.Map;
import mindustry.maps.Maps;
import mindustry.io.SaveIO;

import mindustry.type.Mech;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageAttachment;

import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.zip.InflaterInputStream;

import static mindustry.Vars.*;

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
            throw new RuntimeException("Could not find field 'maps' of class 'mindustry.maps.Maps'");
        }
        mapsField.setAccessible(true);
        this.mapsListField = mapsField;
    }

    public void registerCommands(DiscordCommands handler) {

        handler.registerCommand(new Command("maps") {
            {
                help = "Check a list of available maps and their ids.";
            }
            public void run(Context ctx) {
                StringBuilder msg = new StringBuilder().append("**All available maps in the playlist:**\n```");
                Array<Map> mapList = maps.customMaps();
                for (int i = 0; i < mapList.size; i++) {
                    Map m = mapList.get(i);
                    msg.append(String.valueOf(i)).append(" : ").append(m.name()).append(" : ").append(m.width).append(" x ").append(m.height).append("\n");
                }
                msg.append("```");
                ctx.channel.sendMessage(msg.toString());
            }
        });
        if (data.has("administrator_roleid")) {
            String adminRole = data.getString("administrator_roleid");
            handler.registerCommand(new RoleRestrictedCommand("changemap"){
                {
                    help = "<mapname/mapid> Change the current map to the one provided.";
                    role = adminRole;
                }

                @SuppressWarnings("unchecked")
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    if (ctx.args.length < 2) {
                        eb.setTitle("Command terminated.");
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("Not enough arguments, use `%changemap <mapname|mapid>`".replace("%", IoPlugin.prefix));
                        ctx.channel.sendMessage(eb);
                        return;
                    }
                    Map found = Utils.getMapBySelector(ctx.message.trim());
                    if (found == null) {
                        eb.setTitle("Command terminated.");
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("Map \"" + Utils.escapeCharacters(ctx.message.trim()) + "\" not found!");
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

            handler.registerCommand(new RoleRestrictedCommand("setrank"){
                {
                    help = "<playerid|ip|name> <rank> Change the player's rank to the provided one.";
                    role = adminRole;
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1];
                    int targetRank = Integer.parseInt(ctx.args[2]);
                    if(target.length() > 0 && targetRank > -1 && targetRank < 6) {
                        Player player = Utils.findPlayer(target);
                        if(player!=null){
                            if(IoPlugin.database.containsKey(player.uuid)) {
                                IoPlugin.database.get(player.uuid).setRank(targetRank);
                            } else {
                                IoPlugin.database.put(player.uuid, new PlayerData(targetRank, player.name));
                            }
                            if(targetRank==5) { // give admin to administrators
                                netServer.admins.adminPlayer(player.uuid, player.usid);
                            }
                            eb.setTitle("Command executed successfully");
                            eb.setDescription("Promoted " + Utils.escapeCharacters(player.name) + " to " + targetRank);
                            ctx.channel.sendMessage(eb);
                            Call.onKick(player.con, "Your rank was modified, please rejoin.");
                        } else{
                            if(IoPlugin.database.containsKey(target)){
                                IoPlugin.database.get(target).setRank(targetRank);
                                eb.setTitle("Command executed successfully");
                                eb.setDescription("Promoted `" + target + "` to " + targetRank);
                                ctx.channel.sendMessage(eb);
                            }
                        }
                    }
                }

            });
        }

        if (data.has("exit_roleid")) {
            handler.registerCommand(new RoleRestrictedCommand("exit") {
                {
                    help = "Close the server.";
                    role = data.getString("exit_roleid");
                }
                public void run(Context ctx) {
                    net.dispose();
                    Core.app.exit();
                }
            });
        }
        if (data.has("moderator_roleid")) {
            String banRole = data.getString("moderator_roleid");

            handler.registerCommand(new RoleRestrictedCommand("announce") {
                {
                    help = "<message> Announces a message to in-game chat.";
                    role = banRole;
                }
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    ctx.message = Utils.escapeCharacters(ctx.message);

                    if (ctx.message.length() <= 0) {
                        eb.setTitle("Command terminated");
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("No message given");
                        ctx.channel.sendMessage(eb);
                        return;
                    }

                    for (Player p : playerGroup.all()) {
                        Call.onInfoToast(p.con, ctx.message, 10);
                    }

                    eb.setTitle("Command executed");
                    eb.setDescription("Your message was announced.");
                    ctx.channel.sendMessage(eb);

                }
            });

            handler.registerCommand(new RoleRestrictedCommand("alert") {
                {
                    help = "<playerid|ip|name> <message> Alerts a player(s) using on-screen messages.";
                    role = banRole;
                }
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1].toLowerCase();
                    ctx.message = Utils.escapeCharacters(ctx.message);
                    if (ctx.message == null) {
                        eb.setTitle("Command terminated");
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("No message given");
                        ctx.channel.sendMessage(eb);
                        return;
                    }
                    if(target.equals("all")) {
                        for (Player p : playerGroup.all()) {
                            Call.onInfoMessage(p.con, ctx.message.split(" ", 2)[1]);
                        }
                        eb.setTitle("Command executed");
                        eb.setDescription("Alert was sent to all players.");
                        ctx.channel.sendMessage(eb);
                    } else{
                        Player p = Utils.findPlayer(target);
                        if (p != null) {
                            Call.onInfoMessage(p.con, ctx.message.split(" ", 2)[1]);
                            eb.setTitle("Command executed");
                            eb.setDescription("Alert was sent to " + Utils.escapeCharacters(p.name));
                            ctx.channel.sendMessage(eb);
                        } else {
                            eb.setTitle("Command terminated");
                            eb.setColor(Utils.Pals.error);
                            eb.setDescription("Player could not be found or is offline.");
                            ctx.channel.sendMessage(eb);
                        }
                    }
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("gameover") {
                {
                    help = "Force a game over.";
                    role = banRole;
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

            handler.registerCommand(new RoleRestrictedCommand("ban") {
                {
                    help = "<ip/id> Ban a player by the provided ip or id.";
                    role = banRole;
                }
 
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTimestampToNow();
                    String target = ctx.args[1];
                    if (target.length() > 0) {
                        Player p = Utils.findPlayer(target);
                        if (p != null) {
                            netServer.admins.banPlayer(p.uuid);
                            eb.setTitle("Command executed.");
                            eb.setDescription("Banned " + p.name + "(#" + p.id + ") `" + p.con.address + "` successfully!");
                            ctx.channel.sendMessage(eb);
                            Call.onKick(p.con, "You've been banned by: " + ctx.author.getName() + ". Appeal at http://discord.mindustry.io");
                            Call.sendMessage("[scarlet]" + Utils.escapeCharacters(p.name) + " has been banned permanently.");
                        } else {
                            eb.setTitle("Command terminated");
                            eb.setColor(Utils.Pals.error);
                            eb.setDescription("Player not online. Use %blacklist <ip> to ban an offline player.".replace("%", IoPlugin.prefix));
                            ctx.channel.sendMessage(eb);
                        }
                    } else {
                        eb.setTitle("Command terminated");
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("Not enough arguments / usage: `%ban <id/ip>`".replace("%", IoPlugin.prefix));
                        ctx.channel.sendMessage(eb);
                    }
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("blacklist") {
                {
                    help = "<ip> Ban a player by the provided ip.";
                    role = banRole;
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTimestampToNow();
                    String target = ctx.args[1];
                    if (target.length() > 0) {
                        netServer.admins.banPlayerIP(target);
                        eb.setTitle("Blacklisted successfully.");
                        eb.setDescription("`" + target + "` was banned.");
                        ctx.channel.sendMessage(eb);
                    } else {
                        eb.setTitle("Command terminated");
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("Not enough arguments / usage: `%blacklist <ip>`".replace("%", IoPlugin.prefix));
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
                    if(ctx.args.length==2){ ip = ctx.args[1]; } else {ctx.reply("Invalid arguments provided, use the following format: %unban <ip>".replace("%", IoPlugin.prefix)); return;}

                    if (netServer.admins.unbanPlayerIP(ip)) {
                        eb.setTitle("Command executed.");
                        eb.setDescription("Unbanned `" + ip + "` successfully");
                        ctx.channel.sendMessage(eb);
                    } else {
                        eb.setTitle("Command terminated.");
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("No such ban exists.");
                        ctx.channel.sendMessage(eb);
                    }
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("kick") {
                EmbedBuilder eb = new EmbedBuilder()
                        .setTimestampToNow();
                {
                    help = "<ip/id> Kick a player by the provided ip or id.";
                    role = banRole;
                }
                public void run(Context ctx) {
                    String target = ctx.args[1];

                    Player p = Utils.findPlayer(target);
                    if (p != null) {
                        eb.setTitle("Command executed.");
                        eb.setDescription("Kicked " + p.name + "(#" + p.id + ") `" + p.con.address + "` successfully.");
                        Call.sendChatMessage("[scarlet]" + Utils.escapeCharacters(p.name) + " has been kicked.");
                        Call.onKick(p.con, "You've been kicked by: " + ctx.author.getName());
                    } else {
                        eb.setTitle("Command terminated.");
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("Player not online / not found.");
                    }
                    ctx.channel.sendMessage(eb);
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("playersinfo") {
                {
                    help = "Check the information about all players on the server.";
                    role = banRole;
                }
                public void run(Context ctx) {
                    StringBuilder msg = new StringBuilder("**Players online: " + playerGroup.size() + "**\n```\n");
                    for (Player player : playerGroup.all()) {
                        msg.append("· ").append(Utils.escapeCharacters(player.name));
                        if(!player.isAdmin) {
                            msg.append(" : ").append(player.con.address).append(" : ").append(player.uuid).append("\n");
                        } else {
                            msg.append("\n");
                        }
                    }
                    msg.append("```");

                    ctx.channel.sendMessage(msg.toString());
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("uploadmap") {
                {
                    help = "<.msav attachment> Upload a new map (Include a .msav file with command message)";
                    role = banRole;
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
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("You need to add one valid .msav file!");
                        ctx.channel.sendMessage(eb);
                        return;
                    } else if (Core.settings.getDataDirectory().child("maps").child(ml.get(0).getFileName()).exists()) {
                        eb.setTitle("Map upload terminated.");
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("There is already a map with this name on the server!");
                        ctx.channel.sendMessage(eb);
                        return;
                    }
                    // more custom filename checks possible

                    CompletableFuture<byte[]> cf = ml.get(0).downloadAsByteArray();
                    Fi fh = Core.settings.getDataDirectory().child("maps").child(ml.get(0).getFileName());

                    try {
                        byte[] data = cf.get();
                        if (!SaveIO.isSaveValid(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data))))) {
                            eb.setTitle("Map upload terminated.");
                            eb.setColor(Utils.Pals.error);
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
                    help = "<mapname/mapid> Remove a map from the playlist (use mapname/mapid retrieved from the %maps command)".replace("%", IoPlugin.prefix);
                    role = banRole;
                }
                @Override
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    if (ctx.args.length < 2) {
                        eb.setTitle("Command terminated.");
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("Not enough arguments, use `%removemap <mapname/mapid>`".replace("%", IoPlugin.prefix));
                        ctx.channel.sendMessage(eb);
                        return;
                    }
                    Map found = Utils.getMapBySelector(ctx.message.trim());
                    if (found == null) {
                        eb.setTitle("Command terminated.");
                        eb.setColor(Utils.Pals.error);
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
            handler.registerCommand(new RoleRestrictedCommand("syncserver") {
                {
                    help = "Tell everyone to resync.";
                    role = banRole;
                }
                public void run(Context ctx) {
                    for(Player p : playerGroup.all()) {
                        Call.onInfoMessage(p.con, "Desync detected, please use the /sync command.");
                    }
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("Command executed.")
                            .setDescription("Synchronized every player's client with the server.");
                    ctx.channel.sendMessage(eb);
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("mech") {
                {
                    help = "<mechname> <playerid|ip|all|name> Change the provided player into a specific mech.";
                    role = banRole;
                }
                public void run(Context ctx) {
                    String target = ctx.args[1];
                    String targetMech = ctx.args[2].toLowerCase();
                    Mech desiredMech = Mechs.alpha;
                    if(target.length() > 0 && targetMech.length() > 0) {
                        switch(targetMech){
                            case "alpha":
                                break;
                            case "delta":
                                desiredMech = Mechs.delta;
                                break;
                            case "tau":
                                desiredMech = Mechs.tau;
                                break;
                            case "dart":
                                desiredMech = Mechs.dart;
                                break;
                            case "glaive":
                                desiredMech = Mechs.glaive;
                                break;
                            case "javelin":
                                desiredMech = Mechs.javelin;
                                break;
                            case "omega":
                                desiredMech = Mechs.omega;
                                break;
                            case "trident":
                                desiredMech = Mechs.trident;
                                break;
                            default:
                                desiredMech = Mechs.starter;
                                break;
                        }

                        EmbedBuilder eb = new EmbedBuilder();

                        if(target.equals("all")) {
                            for (Player p : playerGroup.all()) {
                                p.mech = desiredMech;
                            }
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Changed everyone's mech into " + desiredMech.name);
                            ctx.channel.sendMessage(eb);
                            return;
                        }
                        Player player = Utils.findPlayer(target);
                        if(player!=null){
                            player.mech = desiredMech;
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Changed " + Utils.escapeCharacters(player.name) + "s mech into " + desiredMech.name);
                            ctx.channel.sendMessage(eb);
                        }
                    }
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("changeteam") {
                {
                    help = "<playerid|ip|all|name> <team> Change the provided player's team into the provided one.";
                    role = banRole;
                }
                public void run(Context ctx) {
                    String target = ctx.args[1];
                    String targetTeam = ctx.args[2].toLowerCase();
                    Team desiredTeam = Team.crux;
                    if(target.length() > 0 && targetTeam.length() > 0) {
                        switch(targetTeam){
                            case "crux":
                                break;
                            case "blue":
                                desiredTeam = Team.blue;
                                break;
                            case "derelict":
                                desiredTeam = Team.derelict;
                                break;
                            case "green":
                                desiredTeam = Team.green;
                                break;
                            case "purple":
                                desiredTeam = Team.purple;
                                break;
                            case "sharded":
                                desiredTeam = Team.sharded;
                                break;
                            default:
                                break;
                        }

                        EmbedBuilder eb = new EmbedBuilder();

                        if(target.equals("all")) {
                            for (Player p : playerGroup.all()) {
                                p.setTeam(desiredTeam);
                            }
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Changed everyone's team to " + desiredTeam.name);
                            ctx.channel.sendMessage(eb);
                            return;
                        }
                        Player player = Utils.findPlayer(target);
                        if(player!=null){
                            player.setTeam(desiredTeam);
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Changed " + Utils.escapeCharacters(player.name) + "s team to " + desiredTeam.name);
                            ctx.channel.sendMessage(eb);
                        }
                    }
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("changeteamid") {
                {
                    help = "<playerid|ip|all|name> <team> Change the provided player's team into a generated int.";
                    role = banRole;
                }
                public void run(Context ctx) {
                    String target = ctx.args[1];
                    int targetTeam = Integer.parseInt(ctx.args[2]);
                    if(target.length() > 0 && targetTeam > 0) {
                        EmbedBuilder eb = new EmbedBuilder();

                        if(target.equals("all")) {
                            for (Player p : playerGroup.all()) {
                                p.setTeam(Team.get(targetTeam));
                            }
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Changed everyone's team to " + targetTeam);
                            ctx.channel.sendMessage(eb);
                            return;
                        }
                        Player player = Utils.findPlayer(target);
                        if(player!=null){
                            player.setTeam(Team.get(targetTeam));
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Changed " + Utils.escapeCharacters(player.name) + "s team to " + targetTeam);
                            ctx.channel.sendMessage(eb);
                        }
                    }
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("rename"){
                {
                    help = "<playerid|ip|name> <name> Rename the provided player";
                    role = banRole;
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1];
                    String name = ctx.message.substring(target.length() + 1);
                    if(target.length() > 0 && name.length() > 0) {
                        Player player = Utils.findPlayer(target);
                        if(player!=null){
                            if(IoPlugin.rainbowedPlayers.contains(player.uuid)) { // turn rainbow off if its enabled
                                IoPlugin.rainbowedPlayers.remove(player.uuid);
                            }
                            player.name = name;
                            eb.setTitle("Command executed successfully");
                            eb.setDescription("Changed name to " + Utils.escapeCharacters(player.name));
                            ctx.channel.sendMessage(eb);
                            Call.onInfoToast(player.con, "[scarlet]Your name was changed by a moderator.", 10);
                        }
                    }
                }

            });

            handler.registerCommand(new RoleRestrictedCommand("motd"){
                {
                    help = "<newmessage> Change / set a welcome message";
                    role = banRole;
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle("Command executed successfully");
                    String message = ctx.message;
                    if(message.length() > 0 && !message.equals("disable")) {
                        Utils.welcomeMessage = message;
                        eb.setDescription("Changed welcome message.");
                        ctx.channel.sendMessage(eb);
                    } else {
                        eb.setDescription("Disabled welcome message.");
                        ctx.channel.sendMessage(eb);
                    }
                }

            });

            handler.registerCommand(new RoleRestrictedCommand("getrank"){
                {
                    help = "<rankid> Returns all players and their uuids for the specified rank.";
                    role = banRole;
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    int targetRank = Integer.parseInt(ctx.args[1]);
                    if(targetRank > 0) {
                        StringBuilder msg = new StringBuilder().append("**Players with rank** `").append(Utils.rankNames.get(targetRank)).append("`:```");
                        for(java.util.Map.Entry<String, PlayerData> entrySet : IoPlugin.database.entrySet()) {
                            String uuid = entrySet.getKey();
                            PlayerData pd = entrySet.getValue();
                            if(uuid != null && pd != null) {
                                msg.append("· ").append(Utils.escapeCharacters(Utils.escapeColorCodes(pd.playerName))).append(" : ").append(uuid).append("\n");
                            }
                        }
                        ctx.reply(String.valueOf(msg));
                    }
                }

            });

            /*handler.registerCommand(new RoleRestrictedCommand("teleport") {
                {
                    help = "<playerid|ip|all> <playerid|ip|all> Teleport player1 to player2.";
                    role = banRole;
                }
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();

                    String target1 = ctx.args[1];
                    String target2 = ctx.args[2];

                    Player from = Utils.findPlayer(target1);
                    Player to = Utils.findPlayer(target2);

                    if(target1.equals("all") && to != null) {
                        Administration.Config.strict.set(false);
                        for (Player p : playerGroup.all()) {
                            Call.onPositionSet(p.con, to.getX(), to.getY());
                        }
                        Administration.Config.strict.set(true);
                        eb.setTitle("Command executed successfully.");
                        eb.setDescription("Teleported everyone to (" + to.x + ", " + to.y + ")");
                        ctx.channel.sendMessage(eb);
                        return;
                    }

                    if(from != null && to != null){
                        Administration.Config.strict.set(false);
                        Call.onPositionSet(from.con, to.getX(), to.getY());
                        Administration.Config.strict.set(true);
                        eb.setTitle("Command executed successfully.");
                        eb.setDescription("Teleported " + Utils.escapeCharacters(from.name) +" to (" + to.x + ", " + to.y + ")");
                        ctx.channel.sendMessage(eb);
                    }
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("teleportpos") {
                {
                    help = "<playerid|ip|all> <x> <y> Teleport the provided player(s) into the specified position";
                    role = banRole;
                }
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();

                    String target = ctx.args[1];
                    String tox = ctx.args[2];
                    String toy = ctx.args[3];

                    Player from = Utils.findPlayer(target);
                    Integer x = Integer.parseInt(tox);
                    Integer y = Integer.parseInt(toy);

                    if(target.equals("all") && x != null && y != null) {
                        Administration.Config.strict.set(false);
                        for (Player p : playerGroup.all()) {
                            Call.onPositionSet(p.con, x, y);
                        }
                        Administration.Config.strict.set(true);
                        eb.setTitle("Command executed successfully.");
                        eb.setDescription("Teleported everyone to (" + x + ", " + y + ")");
                        ctx.channel.sendMessage(eb);
                        return;
                    }

                    if(from != null && x != null && y != null){
                        Administration.Config.strict.set(false);
                        Call.onPositionSet(from.con, x, y);
                        Administration.Config.strict.set(true);
                        eb.setTitle("Command executed successfully.");
                        eb.setDescription("Teleported " + Utils.escapeCharacters(from.name) +" to (" + x + ", " + y + ")");
                        ctx.channel.sendMessage(eb);
                    }
                }
            });*/

            //TODO: add a lot of commands that moderators can use to mess with players real-time (e. kill, freeze, teleport, etc.)
        }

        if (data.has("donator_roleid")) {
            String donatorRole = data.getString("donator_roleid");
            handler.registerCommand(new RoleRestrictedCommand("redeemvip"){
                {
                    help = "<playerid|ip> Promote your in-game rank to VIP [NOTE: Abusing this power and giving it to other players will result in a ban.]";
                    role = donatorRole;
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1];
                    if(target.length() > 0) {
                        Player player = Utils.findPlayer(target);
                        if(player!=null){
                            if(IoPlugin.database.containsKey(player.uuid)) {
                                IoPlugin.database.get(player.uuid).setRank(2);
                            } else {
                                IoPlugin.database.put(player.uuid, new PlayerData(2, player.name));
                            }
                            eb.setTitle("Command executed successfully");
                            eb.setDescription("Promoted " + Utils.escapeCharacters(player.name) + " to <vip>.");
                            ctx.channel.sendMessage(eb);
                            Call.onKick(player.con, "Your rank was modified, please rejoin.");
                        }
                    }
                }

            });
            /*handler.registerCommand(new Command("sendm"){ // use sendm to send embed messages when needed locally, disable for now
                public void run(Context ctx){
                    EmbedBuilder eb = new EmbedBuilder()
                            .setColor(Utils.Pals.info)
                            .setTitle("Support mindustry.io by donating, and receive custom ranks!")
                            .setUrl("https://donate.mindustry.io/")
                            .setDescription("By donating, you directly help me pay for the monthly server bills I receive for hosting 4 servers with **150+** concurrent players daily.")
                            .addField("VIP", "**VIP** is obtainable through __nitro boosting__ the server or __donating $1.59+__ to the server.", false)
                            .addField("__**MVP**__", "**MVP** is a more enchanced **vip** rank, obtainable only through __donating $3.39+__ to the server.", false)
                            .addField("Where do I get it?", "You can purchase **vip** & **mvp** ranks here: https://donate.mindustry.io", false)
                            .addField("\uD83E\uDD14 io is pay2win???", "Nope. All perks vips & mvp's gain are aesthetic items **or** items that indirectly help the team. Powerful commands that could give you an advantage are __disabled on pvp.__", true);
                    ctx.channel.sendMessage(eb);
                }
            });*/
        }

        if (data.has("activeplayer_roleid")) {
            String activeRole = data.getString("activeplayer_roleid");
            handler.registerCommand(new RoleRestrictedCommand("redeemactive"){
                {
                    help = "<playerid|ip|name> Promote your in-game rank to active player [NOTE: Abusing this power and giving it to other players will result in a ban.]";
                    role = activeRole;
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1];
                    if(target.length() > 0) {
                        Player player = Utils.findPlayer(target);
                        if(player!=null){
                            if(IoPlugin.database.containsKey(player.uuid)) {
                                IoPlugin.database.get(player.uuid).setRank(1);
                            } else {
                                IoPlugin.database.put(player.uuid, new PlayerData(1, player.name));
                            }
                            eb.setTitle("Command executed successfully");
                            eb.setDescription("Promoted " + Utils.escapeCharacters(player.name) + " to <active player>.");
                            ctx.channel.sendMessage(eb);
                            Call.onKick(player.con, "Your rank was modified, please rejoin.");
                        }
                    }
                }

            });
        }

        if (data.has("mvp_roleid")) {
            String mvpRole = data.getString("mvp_roleid");
            handler.registerCommand(new RoleRestrictedCommand("redeemmvp"){
                {
                    help = "<playerid|ip|name> Promote your in-game rank to MVP [NOTE: Abusing this power and giving it to other players will result in a ban.]";
                    role = mvpRole;
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1];
                    if(target.length() > 0) {
                        Player player = Utils.findPlayer(target);
                        if(player!=null){
                            if(IoPlugin.database.containsKey(player.uuid)) {
                                IoPlugin.database.get(player.uuid).setRank(3);
                            } else {
                                IoPlugin.database.put(player.uuid, new PlayerData(3, player.name));
                            }
                            eb.setTitle("Command executed successfully");
                            eb.setDescription("Promoted " + Utils.escapeCharacters(player.name) + " to <mvp>.");
                            ctx.channel.sendMessage(eb);
                            Call.onKick(player.con, "Your rank was modified, please rejoin.");
                        }
                    }
                }

            });
        }


        if(data.has("mapSubmissions_id")){
            TextChannel tc = IoPlugin.getTextChannel(IoPlugin.data.getString("mapSubmissions_id"));
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
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("You need to add one valid .msav file!");
                        ctx.channel.sendMessage(eb);
                        return;
                    } else if (Core.settings.getDataDirectory().child("maps").child(ml.get(0).getFileName()).exists()) {
                        eb.setTitle("Map upload terminated.");
                        eb.setColor(Utils.Pals.error);
                        eb.setDescription("There is already a map with this name on the server!");
                        ctx.channel.sendMessage(eb);
                        return;
                    }
                    CompletableFuture<byte[]> cf = ml.get(0).downloadAsByteArray();

                    try {
                        byte[] data = cf.get();
                        if (!SaveIO.isSaveValid(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data))))) {
                            eb.setTitle("Map upload terminated.");
                            eb.setColor(Utils.Pals.error);
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
                            .setTitle("A map submission has been made for " + IoPlugin.serverName)
                            .setAuthor(ctx.author)
                            .setTimestampToNow()
                            .addField("Name", ml.get(0).getFileName())
                            .addField("URL", String.valueOf(ml.get(0).getUrl()));
                    tc.sendMessage(eb2);
                }
            });
        }
    }
}