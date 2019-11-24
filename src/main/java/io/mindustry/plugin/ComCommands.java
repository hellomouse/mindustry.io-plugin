package io.mindustry.plugin;

import java.util.ArrayList;
import java.util.List;

import org.javacord.api.entity.message.MessageBuilder;

import io.anuke.mindustry.entities.type.Player;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.game.Teams.TeamData;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.modules.ItemModule;
import io.mindustry.plugin.Utils;
import io.mindustry.plugin.discordcommands.Command;
import io.mindustry.plugin.discordcommands.Context;
import io.mindustry.plugin.discordcommands.DiscordCommands;

import static io.anuke.mindustry.Vars.*;

public class ComCommands {
    public void registerCommands(DiscordCommands handler) {
        handler.registerCommand(new Command("chat") {
            {
                help = "Send a message to ingame chat";
            }
            public void run(Context ctx) {
                if (ctx.message == null) {
                    ctx.reply("No message given");
                    return;
                }
                Call.sendMessage("[sky]" + ctx.author.getName()+ " @discord >[] " + ctx.message);
            }
        });
        handler.registerCommand(new Command("players") {
            {
                help = "Get all online players";
            }
            public void run(Context ctx) {
                List<String> result = new ArrayList<>();
                result.add("There are currently " + playerGroup.size() + " players online");
                for (Player player : playerGroup.all()) {
                    result.add(" * " + player.name + " (#" + player.id + ")");
                }
                ctx.reply(new MessageBuilder().appendCode("", Utils.escapeBackticks(String.join("\n", result))));
            }
        });
        handler.registerCommand(new Command("info") {
            {
                help = "Get server information";
            }
            public void run(Context ctx) {
                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Map: ").append(world.getMap().name()).append("\n").append("author: ").append(world.getMap().author()).append("\n");
                    sb.append("Wave: ").append(state.wave).append("\n");
                    sb.append("Enemies: ").append(state.enemies).append("\n");
                    sb.append("Players: ").append(playerGroup.size()).append('\n');
                    ctx.reply(new MessageBuilder().appendCode("", Utils.escapeBackticks(sb.toString())));
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                    ctx.reply("An error has occured.");
                }
            }
        });
        handler.registerCommand(new Command("resinfo") {
            {
                help = "Get resources currently in core";
            }
            public void run(Context ctx) {
                if (!state.rules.waves) {
                    ctx.reply("Only available in survival mode!");
                    return;
                }
                // the normal player team is "sharded"
                TeamData data = state.teams.get(Team.sharded);
                // FIXME: this assumes there's only one core, perhaps sum up resources of all cores?
                //-- Items are shared between cores, no need.
                Tile core = data.cores.first();
                ItemModule items = core.entity.items;
                List<String> result = new ArrayList<>();
                result.add("Items in the core:");
                items.forEach((item, amount) -> result.add(item.name + ": " + (int)amount));
                ctx.reply(new MessageBuilder().appendCode("", String.join("\n", result)));
            }
        });

        // TODO: add help and list commands or something
    }
}
