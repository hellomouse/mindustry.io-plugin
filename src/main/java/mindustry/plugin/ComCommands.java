package mindustry.plugin;

import arc.files.Fi;
import mindustry.maps.Map;

import mindustry.entities.type.Player;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Call;
import mindustry.plugin.discordcommands.RoleRestrictedCommand;
import mindustry.world.blocks.storage.CoreBlock.CoreEntity;
import mindustry.world.modules.ItemModule;
import mindustry.plugin.discordcommands.Command;
import mindustry.plugin.discordcommands.Context;
import mindustry.plugin.discordcommands.DiscordCommands;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;

import java.util.ArrayList;
import java.util.List;

import static mindustry.Vars.*;
import static mindustry.plugin.Utils.*;

public class ComCommands {
    public void registerCommands(DiscordCommands handler) {
        handler.registerCommand(new Command("chat") {
            {
                help = "<message> Sends a message to in-game chat.";
            }
            public void run(Context ctx) {
                EmbedBuilder eb = new EmbedBuilder();
                ctx.message = escapeCharacters(ctx.message);
                if (ctx.message == null) {
                    eb.setTitle("Command terminated");
                    eb.setDescription("No message given");
                    ctx.channel.sendMessage(eb);
                    return;
                }
                if (ctx.message.length() < chatMessageMaxSize) {
                    Call.sendMessage("[sky]" + ctx.author.getName() + " @discord >[] " + ctx.message);
                    eb.setTitle("Command executed");
                    eb.setDescription("Your message was sent successfully..");
                    ctx.channel.sendMessage(eb);
                } else{
                    ctx.reply("Message too big.");
                }
            }
        });
        handler.registerCommand(new Command("downloadmap") {
            {
                help = "<mapname/mapid> Preview and download a server map in a .msav file format.";
            }
            public void run(Context ctx) {
                if (ctx.args.length < 2) {
                    ctx.reply("Not enough arguments, use `%map <mapname/mapid>`".replace("%", ioMain.prefix));
                    return;
                }

                Map found = getMapBySelector(ctx.message.trim());
                if (found == null) {
                    ctx.reply("Map not found!");
                    return;
                }

                Fi mapFile = found.file;

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle(escapeCharacters(found.name()))
                        .setDescription(escapeCharacters(found.description()))
                        .setAuthor(escapeCharacters(found.author()));
                // TODO: .setImage(mapPreviewImage)
                ctx.channel.sendMessage(embed, mapFile.file());
            }
        });
        handler.registerCommand(new Command("players") {
            {
                help = "Check who is online and their ids.";
            }
            public void run(Context ctx) {
                StringBuilder msg = new StringBuilder("**Players online: " + playerGroup.size() + "**\n```\n");
                for (Player player : playerGroup.all()) {
                    msg.append("· ").append(escapeCharacters(player.name)).append(" : ").append(player.id).append("\n");
                }
                msg.append("```");
                ctx.channel.sendMessage(msg.toString());
            }
        });
        handler.registerCommand(new Command("info") {
            {
                help = "Get basic server information.";
            }
            public void run(Context ctx) {
                try {
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle(ioMain.serverName)
                            .addField("Players", String.valueOf(playerGroup.size()))
                            .addField("Map", world.getMap().name())
                            .addField("Wave", String.valueOf(state.wave))
                            .addField("Next wave in", Math.round(state.wavetime / 60) + " seconds.");

                    ctx.channel.sendMessage(eb);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                    ctx.reply("An error has occurred.");
                }
            }
        });
        handler.registerCommand(new Command("resinfo") {
            {
                help = "Check the amount of resources in the core.";
            }
            public void run(Context ctx) {
                if (!state.rules.waves) {
                    ctx.reply("Only available in survival mode!");
                    return;
                }
                // the normal player team is "sharded"
                TeamData data = state.teams.get(Team.sharded);
                //-- Items are shared between cores
                CoreEntity core = data.cores.first();
                ItemModule items = core.items;
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("Resources in the core:");
                items.forEach((item, amount) -> eb.addInlineField(item.name, String.valueOf(amount)));
                ctx.channel.sendMessage(eb);
            }
        });

        handler.registerCommand(new Command("help") {
            {
                help = "Display all available commands and their usage.";
            }
            public void run(Context ctx) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("All available commands:");
                for(Command command : handler.getAllCommands()) {
                    embed.addInlineField(command.name, command.help);
                }
                ctx.channel.sendMessage(embed);
            }
        });

        handler.registerCommand(new Command("redeem"){
            {
                help = "<name|id> Promote your in-game rank. [NOTE: Abusing this power and giving it to other players will result in a ban.]";
            }

            public void run(Context ctx) {
                EmbedBuilder eb = new EmbedBuilder();
                String target = "";
                if(ctx.args.length > 1) {
                    target = ctx.args[1];
                }
                List<Role> authorRoles = ctx.author.asUser().get().getRoles(ctx.event.getServer().get()); // javacord gay
                List<String> roles = new ArrayList<>();
                for(Role r : authorRoles){
                    if(r!=null) {
                        roles.add(r.getIdAsString());
                    }
                }
                if(target.length() > 0 && roles != null) {
                    int rank = 0;
                    for(String role : roles){
                        if(rankRoles.containsKey(role)){
                            if(rankRoles.get(role) > rank) { rank = rankRoles.get(role); }
                        }
                    }
                    Player player = findPlayer(target);
                    if(player!=null && rank > 0){
                        if(ioMain.database.containsKey(player.uuid)) {
                            ioMain.database.get(player.uuid).setRank(rank);
                        } else {
                            ioMain.database.put(player.uuid, new PlayerData(rank));
                        }
                        eb.setTitle("Command executed successfully");
                        eb.setDescription("Promoted " + escapeCharacters(player.name) + " to " + escapeColorCodes(rankNames.get(rank)) + ".");
                        ctx.channel.sendMessage(eb);
                        Call.onKick(player.con, "Your rank was modified, please rejoin.");
                    } else {
                        eb.setTitle("Command terminated");
                        eb.setDescription("Player not online or not found.");
                        ctx.channel.sendMessage(eb);
                    }
                } else {
                    eb.setTitle("Command terminated");
                    eb.setDescription("Invalid arguments provided or no roles to redeem.");
                    ctx.channel.sendMessage(eb);
                }
            }

        });
    }
}
