package disc.command;

//mindustry + arc

import disc.utils;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.entities.type.Player;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.world.modules.ItemModule;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

//javacord


public class comCommands implements MessageCreateListener {
    @Override
    public void onMessageCreate(MessageCreateEvent event){
        if (event.getMessageContent().startsWith(".chat ")){
            //discord -> server
            String[] msg = event.getMessageContent().split(" ", 2);
            Call.sendMessage("[sky]" +event.getMessageAuthor().getName()+ " @discord >[] " + msg[1].trim());
        }

        //playerlist
        else if (event.getMessageContent().equalsIgnoreCase(".players")){
            StringBuilder lijst = new StringBuilder();
            lijst.append("players: ").append(Vars.playerGroup.size()).append("\n");
            for (Player p :Vars.playerGroup.all()){
                lijst.append("* ").append(p.name.trim()).append("\n");
            }
            new MessageBuilder().appendCode("", utils.escapeBackticks(lijst.toString())).send(event.getChannel());
        }
        //info
        else if (event.getMessageContent().equalsIgnoreCase(".info")){
            try {
                StringBuilder lijst = new StringBuilder();
                lijst.append("Map: ").append(Vars.world.getMap().name()).append("\n").append("author: ").append(Vars.world.getMap().author()).append("\n");
                lijst.append("Wave: ").append(Vars.state.wave).append("\n");
                lijst.append("Enemies: ").append(Vars.state.enemies).append("\n");
                lijst.append("Players: ").append(Vars.playerGroup.size()).append('\n');
                new MessageBuilder().appendCode("", utils.escapeBackticks(lijst.toString())).send(event.getChannel());
            } catch (Exception e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        }

        else if (event.getMessageContent().equalsIgnoreCase(".infores")){
            if (!Vars.state.rules.waves){
                event.getChannel().sendMessage("Only available when playing survival mode!");
                return;
            } else if(Vars.playerGroup.isEmpty()) {
                event.getChannel().sendMessage("No players online!"); // requires 1 player at least to call getClosestCore()
            } else {
                StringBuilder lijst = new StringBuilder();
                lijst.append("Items in the core:\n");
                ItemModule core = Vars.playerGroup.all().get(0).getClosestCore().items;
                lijst.append("Copper: ").append(core.get(Items.copper)).append("\n");
                lijst.append("Lead: ").append(core.get(Items.lead)).append("\n");
                lijst.append("Graphite: ").append(core.get(Items.graphite)).append("\n");
                lijst.append("Metaglass: ").append(core.get(Items.metaglass)).append("\n");
                lijst.append("Titanium: ").append(core.get(Items.titanium)).append("\n");
                lijst.append("Thorium: ").append(core.get(Items.thorium)).append("\n");
                lijst.append("Silicon: ").append(core.get(Items.silicon)).append("\n");
                lijst.append("Plastanium: ").append(core.get(Items.plastanium)).append("\n");
                lijst.append("Phase fabric: ").append(core.get(Items.phasefabric)).append("\n");
                lijst.append("Surge alloy: ").append(core.get(Items.surgealloy)).append("\n");

                new MessageBuilder().appendCode("", utils.escapeBackticks(lijst.toString())).send(event.getChannel());
            }


        }

    }
}
