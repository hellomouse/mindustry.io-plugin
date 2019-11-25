package io.mindustry.plugin.discordcommands;

import java.util.Collection;
import java.util.HashMap;

import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

/** Represents a registry of commands */
public class DiscordCommands implements MessageCreateListener {
    public final String commandPrefix = ".";
    private HashMap<String, Command> registry = new HashMap<>();

    public DiscordCommands() {
        // stuff
    }
    /**
     * Register a command in the CommandRegistry
     * @param c The command
     */
    public void registerCommand(Command c) {
        registry.put(c.name.toLowerCase(), c);
    }
    // you can override the name of the command manually, for example for aliases
    /**
     * Register a command in the CommandRegistry
     * @param forcedName Register the command under another name
     * @param c The command to register
     */
    public void registerCommand(String forcedName, Command c) {
        registry.put(forcedName.toLowerCase(), c);
    }
    /**
     * Parse and run a command
     * @param event Source event associated with the message
     */
    public void onMessageCreate(MessageCreateEvent event) {
        String message = event.getMessageContent();
        if (!message.startsWith(commandPrefix)) return;
        String[] args = message.split(" ");
        int commandLength = args[0].length();
        args[0] = args[0].substring(commandPrefix.length());
        String name = args[0];

        String newMessage = null;
        if (args.length > 1) newMessage = message.substring(commandLength + 1);
        runCommand(name, new Context(event, args, newMessage));
    }
    /**
     * Run a command
     * @param name
     * @param ctx
     */
    public void runCommand(String name, Context ctx) {
        Command command = registry.get(name.toLowerCase());
        if (command == null) {
            ctx.reply("No such command");
            return;
        }
        if (!command.hasPermission(ctx)) {
            ctx.reply("No permission");
            return;
        }
        command.run(ctx);
    }
    /**
     * Get a command by name
     * @param name
     * @return
     */
    public Command getCommand(String name) {
        return registry.get(name.toLowerCase());
    }
    /**
     * Get all commands in the registry
     * @return
     */
    public Collection<Command> getAllCommands() {
        return registry.values();
    }
    /**
     * Check if a command exists in the registry
     * @param name
     * @return
     */
    public boolean isCommand(String name) {
        return registry.containsKey(name.toLowerCase());
    }
}