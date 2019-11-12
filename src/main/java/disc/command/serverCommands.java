package disc.command;

import io.anuke.arc.Core;
import io.anuke.arc.Events;
import io.anuke.arc.files.FileHandle;
import io.anuke.arc.util.Log;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.core.GameState;
import io.anuke.mindustry.entities.type.Player;
import io.anuke.mindustry.game.EventType.GameOverEvent;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.maps.Map;
import io.anuke.mindustry.net.Administration;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;
import org.json.JSONObject;

import java.util.Optional;

import static io.anuke.mindustry.Vars.netServer;

public class serverCommands implements MessageCreateListener {
    final long minMapChangeTime = 30L; //30 seconds
    final String commandDisabled = "This command is disabled.";
    final String noPermission = "You don't have permissions to use this command!";

    private JSONObject data;


    public serverCommands(JSONObject _data){
        this.data = _data;
    }

    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        if (event.getMessageContent().equalsIgnoreCase(".gameover")) {
            if (!data.has("gameOver_role_id")){
                if (event.isPrivateMessage()) return;
                event.getChannel().sendMessage(commandDisabled);
                return;
            }
            Role r = getRole(event.getApi(), data.getString("gameOver_role_id"));

            if (!hasPermission(r, event)) return;
            // ------------ has permission --------------
            if (Vars.state.is(GameState.State.menu)) {
                return;
            }
            //inExtraRound = false;
            Events.fire(new GameOverEvent(Team.crux));
        } else if(event.getMessageContent().equalsIgnoreCase(".maps")){
            StringBuilder mapLijst = new StringBuilder();
            mapLijst.append("List of available maps:\n");
            for (Map m:Vars.maps.customMaps()){
                mapLijst.append("* "+m.name() + "/ " + m.width + " x " + m.height+"\n");
            }
            mapLijst.append("Total number of maps: " + Vars.maps.customMaps().size);
            new MessageBuilder().appendCode("", mapLijst.toString()).send(event.getChannel());

        } else if (event.getMessageContent().startsWith(".changemap")){
            if (!data.has("changeMap_role_id")){
                if (event.isPrivateMessage()) return;
                event.getChannel().sendMessage(commandDisabled);
                return;
            }
            Role r = getRole(event.getApi(), data.getString("changeMap_role_id"));
            if (!hasPermission(r, event)) return;


            String[] splitted = event.getMessageContent().split(" ", 2);
            if (splitted.length == 1){
                int index = 1;
                StringBuilder sb = new StringBuilder();
                for (Map m: Vars.maps.customMaps()){
                    sb.append(index++ + " : " + m.name() + "\n");
                }
                sb.append("\nUse .changemap <number/name>");
                new MessageBuilder().appendCode("", sb.toString()).send(event.getChannel());
            } else {
                //try number
                Map found = null;
                try{
                    splitted[1] = splitted[1].trim();
                    found = Vars.maps.customMaps().get(Integer.parseInt(splitted[1]) - 1);
                } catch (Exception e) {
                    //check if map exits
                    for (Map m : Vars.maps.customMaps()) {
                        if (m.name().equals(splitted[1])) {
                            found = m;
                            break;
                        }
                    }
                }
                if (found == null){
                    event.getChannel().sendMessage("Map not found.");
                    return;
                };

                FileHandle temp = Core.settings.getDataDirectory().child("maps/temp");
                temp.mkdirs();

                for (Map m1 : Vars.maps.customMaps()) {
                    if (m1.equals(Vars.world.getMap())) continue;
                    if (m1.equals(found)) continue;
                    m1.file.moveTo(temp);
                }
                //reload all maps from that folder
                Vars.maps.reload();
                //Call gameover
                Events.fire(new GameOverEvent(Team.crux));
                //move maps
                Vars.maps.reload();
                FileHandle mapsDir = Core.settings.getDataDirectory().child("maps");
                for (FileHandle fh : temp.list()) {
                    fh.moveTo(mapsDir);
                }
                temp.deleteDirectory();
                Vars.maps.reload();

                event.getChannel().sendMessage("Next map selected: " + found.name() + "\nThe current map will change in 10 seconds.");
            }

        } else if (event.getMessageContent().startsWith(".exit")){
            if (!data.has("closeServer_role_id")){
                if (event.isPrivateMessage()) return;
                event.getChannel().sendMessage(commandDisabled);
                return;
            }
            Role r = getRole(event.getApi(), data.getString("closeServer_role_id"));
            if (!hasPermission(r, event)) return;

            Vars.net.dispose(); //todo: check
            Core.app.exit();

        } else if (event.getMessageContent().startsWith(".ban")){
            if (!data.has("banPlayers_role_id")){
                if (event.isPrivateMessage()) return;
                event.getChannel().sendMessage(commandDisabled);
                return;
            }
            Role r = getRole(event.getApi(), data.getString("banPlayers_role_id"));
            if (!hasPermission(r, event)) return;

            String[] splitted = event.getMessageContent().split(" ", 2);
            String playerIp = splitted[splitted.length-1];
            if (playerIp!=null){
                for (Player p:Vars.playerGroup.all()){
                    Administration.TraceInfo info = new Administration.TraceInfo(p.con.address, p.uuid, p.con.modclient, p.con.mobile);
                    if (info.ip.equals(playerIp)){
                        netServer.admins.banPlayerIP(playerIp);
                        Call.onKick(p.con, "You've been banned by: " + event.getMessageAuthor().getName() + ".");
                        event.getChannel().sendMessage("Banned `" + playerIp + "` successfully.");
                    }
                }
            } else{
                event.getChannel().sendMessage("Invalid argument / usage: `.ban {ip}`");
            }

        } else if (event.getMessageContent().startsWith(".kick")){

            if (!data.has("kickPlayers_role_id")){
                if (event.isPrivateMessage()) return;
                event.getChannel().sendMessage(commandDisabled);
                return;
            }
            Role r = getRole(event.getApi(), data.getString("kickPlayers_role_id"));
            if (!hasPermission(r, event)) return;

            String[] splitted = event.getMessageContent().split(" ", 2);
            String playerIp = splitted[splitted.length-1];
            if (playerIp!=null){
                for (Player p:Vars.playerGroup.all()){
                    Administration.TraceInfo info = new Administration.TraceInfo(p.con.address, p.uuid, p.con.modclient, p.con.mobile);
                    if (info.ip.equals(playerIp)){
                        Call.onKick(p.con, "You've been kicked by " + event.getMessageAuthor().getName() + ".");
                        event.getChannel().sendMessage("Kicked `" + playerIp + "` successfully.");
                    }
                }
            } else{
                event.getChannel().sendMessage("Invalid argument / usage: `.kick {ip}`");
            }


        } else if (event.getMessageContent().equalsIgnoreCase(".playersinfo")){
            if (!data.has("banPlayers_role_id")){
                if (event.isPrivateMessage()) return;
                event.getChannel().sendMessage(commandDisabled);
                return;
            }

            Role r = getRole(event.getApi(), data.getString("spyPlayers_role_id"));
            if (!hasPermission(r, event)) return;

            StringBuilder lijst = new StringBuilder();
            lijst.append("Players: " + Vars.playerGroup.size()+"\n");
            for (Player p :Vars.playerGroup.all()){
                Administration.TraceInfo info = new Administration.TraceInfo(p.con.address, p.uuid, p.con.modclient, p.con.mobile);
                String p_ip = info.ip;
                if (netServer.admins.isAdmin(p.uuid, p.usid)){p_ip = "~hidden~";}
                lijst.append("* " + p.name.trim() + " : " + p_ip + "\n");
            }
            new MessageBuilder().appendCode("", lijst.toString()).send(event.getChannel());
        }

    }

    public Role getRole(DiscordApi api, String id){
        Optional<Role> r1 = api.getRoleById(id);
        if (!r1.isPresent()) {
            Log.err("[ERR!] discordplugin: role not found!");
            return null;
        }
        return r1.get();
    }

    public Boolean hasPermission(Role r, MessageCreateEvent event){
        try {
            if (r == null) {
                if (event.isPrivateMessage()) return false;
                event.getChannel().sendMessage(commandDisabled);
                return false;
            } else if (!event.getMessageAuthor().asUser().get().getRoles(event.getServer().get()).contains(r)) {
                if (event.isPrivateMessage()) return false;
                event.getChannel().sendMessage(noPermission);
                return false;
            } else {
                return true;
            }
        } catch (Exception e){
            return false;
        }
    }


}