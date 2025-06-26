package com.andyoctopus.customtext;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomText extends JavaPlugin {

    private Map<String, CommandConfig> commands = new HashMap<>();
    private CommandMap commandMap;
    private List<Command> dynamicCommands = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            // 通过反射获取CommandMap (兼容1.21.4)
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());
        } catch (Exception e) {
            getLogger().severe("命令映射获取失败: " + e.getMessage());
        }

        registerBaseCommand();
        reloadCommands();
        getLogger().info("CustomText v1.0 enabled for 1.21.4 with PlaceholderAPI support!");
    }

    private void registerBaseCommand() {
        PluginCommand cmd = getCommand("customtext");
        if (cmd != null) {
            cmd.setExecutor(this);
        } else {
            getLogger().warning("Can't get regular command...");
            cmd = createCommand("customtext");
            if (cmd != null && commandMap != null) {
                cmd.setExecutor(this);
                commandMap.register(getDescription().getName(), cmd);
            }
        }
    }

    private void reloadCommands() {
        unregisterDynamicCommands();
        commands.clear();

        reloadConfig();
        FileConfiguration config = getConfig();

        if (config.contains("commands")) {
            ConfigurationSection commandsSection = config.getConfigurationSection("commands");
            for (String commandName : commandsSection.getKeys(false)) {
                ConfigurationSection cmdSection = commandsSection.getConfigurationSection(commandName);

                CommandConfig cmdConfig = new CommandConfig();
                cmdConfig.permission = cmdSection.getString("permission", null);
                cmdConfig.messages = cmdSection.getStringList("messages");
                commands.put(commandName.toLowerCase(), cmdConfig);

                PluginCommand cmd = createCommand(commandName);
                if (cmd != null && commandMap != null) {
                    cmd.setExecutor(new TextCommandExecutor(this, commandName));
                    commandMap.register(getDescription().getName(), cmd);
                    dynamicCommands.add(cmd);
                    getLogger().info("Command registered: /" + commandName);
                }
            }
        }
    }

    private void unregisterDynamicCommands() {
        if (commandMap == null) return;

        try {
            Field knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            for (Command cmd : dynamicCommands) {
                knownCommands.values().removeIf(c -> c == cmd);
            }
            dynamicCommands.clear();
            getLogger().info("Dynamic command was unloaded");
        } catch (Exception e) {
            getLogger().warning("Failed to unload command: " + e.getMessage());
        }
    }

    private PluginCommand createCommand(String name) {
        try {
            Constructor<PluginCommand> c = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            c.setAccessible(true);
            return c.newInstance(name, this);
        } catch (Exception e) {
            getLogger().severe("Failed to create command: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("customtext")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("customtext.reload")) {
                    reloadCommands();
                    sender.sendMessage(ChatColor.GREEN + "Config reloaded successfully!");
                    return true;
                } else {
                    sender.sendMessage(ChatColor.RED + "You don't have permission!");
                    return true;
                }
            }
            sender.sendMessage(ChatColor.YELLOW + "Usage: /customtext reload");
            return true;
        }
        return false;
    }

    public static class TextCommandExecutor implements org.bukkit.command.CommandExecutor {
        private final CustomText plugin;
        private final String commandName;

        public TextCommandExecutor(CustomText plugin, String commandName) {
            this.plugin = plugin;
            this.commandName = commandName;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            CommandConfig cmdConfig = plugin.commands.get(commandName);
            if (cmdConfig == null) return false;

            if (cmdConfig.permission != null && !sender.hasPermission(cmdConfig.permission)) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            for (String message : cmdConfig.messages) {
                String formatted = ChatColor.translateAlternateColorCodes('&', message);

                // 添加PlaceholderAPI支持
                if (sender instanceof Player && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    Player player = (Player) sender;
                    formatted = PlaceholderAPI.setPlaceholders(player, formatted);
                }
                sender.sendMessage(formatted);
            }
            return true;
        }
    }

    private static class CommandConfig {
        String permission;
        List<String> messages = new ArrayList<>();
    }

    @Override
    public void onDisable() {
        unregisterDynamicCommands();
        getLogger().info("CustomText was disabled.");
    }
}
