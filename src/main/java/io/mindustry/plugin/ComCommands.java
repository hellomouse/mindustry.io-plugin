package io.mindustry.plugin;

import io.anuke.arc.files.FileHandle;
import io.anuke.mindustry.maps.Map;


import io.anuke.mindustry.entities.type.Player;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.game.Teams.TeamData;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.modules.ItemModule;
import io.mindustry.plugin.discordcommands.Command;
import io.mindustry.plugin.discordcommands.Context;
import io.mindustry.plugin.discordcommands.DiscordCommands;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import static io.anuke.mindustry.Vars.*;

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
                    ctx.reply("Message too big, please use a maximum of " + String.valueOf(Utils.chatMessageMaxSize) + " characters.");
                }
            }
        });
        handler.registerCommand(new Command("map") {
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

                FileHandle mapFile = found.file;

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
                    eb.addInlineField(Utils.escapeBackticks(player.name), " (#" + player.id + ")");
                }
                ctx.channel.sendMessage(eb);
            }
        });
        handler.registerCommand(new Command("info") {
            {
                help = "Check basic server information.";
            }
            public void run(Context ctx) {
                try {
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("mindustry.io")
                            .addInlineField("Players", String.valueOf(playerGroup.size()))
                            .addInlineField("Map", world.getMap().name())
                            .addInlineField("Wave", String.valueOf(state.wave))
                            .addInlineField("Next wave in", String.valueOf(Math.round(state.wavetime / 60) + " seconds."));
                    /*StringBuilder sb = new StringBuilder();
                    sb.append("Map: ").append(world.getMap().name()).append("\n").append("author: ").append(world.getMap().author()).append("\n");
                    sb.append("Wave: ").append(state.wave).append("\n");
                    sb.append("Enemies: ").append(state.enemies).append("\n");
                    sb.append("Players: ").append(playerGroup.size()).append('\n');
                    ctx.reply(new MessageBuilder().appendCode("", Utils.escapeBackticks(sb.toString())));*/
                    ctx.channel.sendMessage(eb);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                    ctx.reply("An error has occured.");
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
                Tile core = data.cores.first();
                ItemModule items = core.entity.items;
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
