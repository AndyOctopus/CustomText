package com.andyoctopus.customtext;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
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
    private List<Command> dynamicCommands = new ArrayList<>(); // 存储动态命令

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            // 获取服务器的命令映射
            Field commandMapField = getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            commandMap = (CommandMap) commandMapField.get(getServer());
        } catch (Exception e) {
            getLogger().severe("命令映射获取失败: " + e.getMessage());
        }

        // 安全注册基础命令
        registerBaseCommand();
        reloadCommands();
        getLogger().info("CustomText v1.0 enabled!");
    }

    private void registerBaseCommand() {
        PluginCommand cmd = getCommand("customtext");
        if (cmd != null) {
            cmd.setExecutor(this);
        } else {
            getLogger().warning("Can't get reguler command...");
            cmd = createCommand("customtext");
            if (cmd != null) {
                cmd.setExecutor(this);
                commandMap.register(getDescription().getName(), cmd);
            }
        }
    }

    // 重载所有命令配置
    private void reloadCommands() {
        unregisterDynamicCommands(); // 只卸载动态命令
        commands.clear();

        reloadConfig(); // 重载配置文件
        FileConfiguration config = getConfig();

        if (config.contains("commands")) {
            ConfigurationSection commandsSection = config.getConfigurationSection("commands");
            for (String commandName : commandsSection.getKeys(false)) {
                ConfigurationSection cmdSection = commandsSection.getConfigurationSection(commandName);

                // 解析命令配置
                CommandConfig cmdConfig = new CommandConfig();
                cmdConfig.permission = cmdSection.getString("permission", null);
                cmdConfig.messages = cmdSection.getStringList("messages");
                commands.put(commandName.toLowerCase(), cmdConfig);

                // 动态创建命令
                PluginCommand cmd = createCommand(commandName);
                if (cmd != null) {
                    cmd.setExecutor(new TextCommandExecutor(this, commandName));
                    commandMap.register(getDescription().getName(), cmd);
                    dynamicCommands.add(cmd); // 添加到动态命令列表
                    getLogger().info("Command registered: /" + commandName);
                }
            }
        }
    }

    // 卸载动态命令（不干扰基础命令）
    private void unregisterDynamicCommands() {
        if (commandMap == null) return;

        try {
            Field knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            // 只移除动态命令
            for (Command cmd : dynamicCommands) {
                knownCommands.values().removeIf(c -> c == cmd);
            }
            dynamicCommands.clear();
            getLogger().info("Dynamic command was unloaded");
        } catch (Exception e) {
            getLogger().warning("Failed to unload command: " + e.getMessage());
        }
    }

    // 反射创建命令对象
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

    // 处理/customtext命令
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

    // 动态命令执行器
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

            // 权限检查
            if (cmdConfig.permission != null && !sender.hasPermission(cmdConfig.permission)) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            // 发送配置的消息
            for (String message : cmdConfig.messages) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
            return true;
        }
    }

    // 命令配置存储类
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