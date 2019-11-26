package io.mindustry.plugin;

import io.mindustry.plugin.discordcommands.DiscordCommands;
import io.mindustry.plugin.discordcommands.MessageCreatedListener;
import org.json.JSONObject;

public class MessageCreatedListeners {
    private final JSONObject data;

    public MessageCreatedListeners(JSONObject data){
        this.data = data;
    }
    public void registerListeners(DiscordCommands handler){
        
    }
}
