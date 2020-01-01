package mindustry.plugin;

import arc.files.Fi;
import mindustry.maps.Map;



import mindustry.entities.type.Player;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Call;
import mindustry.world.blocks.storage.CoreBlock.CoreEntity;
import mindustry.world.modules.ItemModule;
import mindustry.plugin.discordcommands.Command;
import mindustry.plugin.discordcommands.Context;
import mindustry.plugin.discordcommands.DiscordCommands;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import static mindustry.Vars.*;

public class ComCommands {
    public void registerCommands(DiscordCommands handler) {
        handler.registerCommand(new Command("chat") {
            {
                help = "<message> Sends a message to in-game chat.";
            }
            public void run(Context ctx) {
                ctx.message = Utils.escapeBackticks(ctx.message);
                if (ctx.message == null) {
                    ctx.reply("No message given");
                    return;
                }
                if (ctx.message.length() < Utils.chatMessageMaxSize) {
                    Call.sendMessage("[sky]" + ctx.author.getName() + " @discord >[] " + ctx.message);
                    ctx.reply("``" + ctx.author.getName() + "@discord > " + ctx.message + "``\nSent successfully.");
                } else{
                    ctx.reply("Message too big, please use a maximum of " + Utils.chatMessageMaxSize + " characters.");
                }
            }
        });
        handler.registerCommand(new Command("downloadmap") {
            {
                help = "<mapname/mapid> Preview and download a server map in a .msav file format.";
            }
            public void run(Context ctx) {
                if (ctx.args.length < 2) {
                    ctx.reply("Not enough arguments, use `.map <mapname/mapid>`");
                    return;
                }

                Map found = Utils.getMapBySelector(ctx.message.trim());
                if (found == null) {
                    ctx.reply("Map not found!");
                    return;
                }

                Fi mapFile = found.file;

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle(Utils.escapeBackticks(found.name()))
                        .setDescription(Utils.escapeBackticks(found.description()))
                        .setAuthor(Utils.escapeBackticks(found.author()));
                // TODO: .setImage(mapPreviewImage)
                ctx.channel.sendMessage(embed, mapFile.file());
            }
        });
        handler.registerCommand(new Command("players") {
            {
                help = "Check who is online and their ids.";
            }
            public void run(Context ctx) {
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("Players online: " + playerGroup.size());
                for (Player player : playerGroup.all()) {
                    eb.addField(Utils.escapeBackticks(player.name), " (#" + player.id + ")");
                }
                ctx.channel.sendMessage(eb);
            }
        });
        handler.registerCommand(new Command("info") {
            {
                help = "Get basic server information.";
            }
            public void run(Context ctx) {
                try {
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("mindustry.io")
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
    }
}
