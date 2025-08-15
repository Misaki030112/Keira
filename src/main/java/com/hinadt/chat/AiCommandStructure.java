package com.hinadt.chat;

import java.util.Arrays;
import java.util.List;

/**
 * AI命令结构定义
 * 使用枚举和树形结构管理所有AI相关命令
 */
public class AiCommandStructure {
    
    /**
     * 主命令枚举
     */
    public enum MainCommand {
        AI("ai", "Ausuka.Ai助手主命令", Permission.USER);
        
        private final String command;
        private final String description;
        private final Permission requiredPermission;
        
        MainCommand(String command, String description, Permission requiredPermission) {
            this.command = command;
            this.description = description;
            this.requiredPermission = requiredPermission;
        }
        
        public String getCommand() { return command; }
        public String getDescription() { return description; }
        public Permission getRequiredPermission() { return requiredPermission; }
    }
    
    /**
     * AI子命令枚举
     */
    public enum AiSubCommand {
        CHAT("chat", "进入AI对话模式", Permission.USER, 
             "开始与Ausuka.Ai助手对话，获得智能帮助"),
             
        EXIT("exit", "退出AI对话模式", Permission.USER,
             "退出当前AI对话模式，返回普通聊天"),
             
        NEW("new", "开始新的对话会话", Permission.USER,
             "清除当前对话历史，开始全新的对话会话"),
             
        ADMIN("admin", "管理员专用功能", Permission.ADMIN,
             "访问管理员专用的AI系统控制功能");
        
        private final String command;
        private final String description;
        private final Permission requiredPermission;
        private final String detailedDescription;
        
        AiSubCommand(String command, String description, Permission requiredPermission, String detailedDescription) {
            this.command = command;
            this.description = description;
            this.requiredPermission = requiredPermission;
            this.detailedDescription = detailedDescription;
        }
        
        public String getCommand() { return command; }
        public String getDescription() { return description; }
        public Permission getRequiredPermission() { return requiredPermission; }
        public String getDetailedDescription() { return detailedDescription; }
    }
    
    /**
     * 管理员子命令枚举
     */
    public enum AdminSubCommand {
        AUTO_MSG("auto-msg", "自动消息系统控制", Permission.ADMIN),
        CONFIG("config", "AI系统配置管理", Permission.ADMIN),
        PERMISSIONS("permissions", "MOD权限管理", Permission.ADMIN),
        STATS("stats", "系统统计信息", Permission.ADMIN),
        RELOAD("reload", "重新加载AI系统", Permission.ADMIN);
        
        private final String command;
        private final String description;
        private final Permission requiredPermission;
        
        AdminSubCommand(String command, String description, Permission requiredPermission) {
            this.command = command;
            this.description = description;
            this.requiredPermission = requiredPermission;
        }
        
        public String getCommand() { return command; }
        public String getDescription() { return description; }
        public Permission getRequiredPermission() { return requiredPermission; }
    }
    
    /**
     * 自动消息系统子命令
     */
    public enum AutoMsgSubCommand {
        TOGGLE("toggle", "切换自动消息系统开关", Permission.ADMIN),
        STATUS("status", "查看自动消息系统状态", Permission.ADMIN),
        BROADCAST("broadcast", "控制广播消息开关", Permission.ADMIN),
        PERSONAL("personal", "控制个人消息开关", Permission.ADMIN);
        
        private final String command;
        private final String description;
        private final Permission requiredPermission;
        
        AutoMsgSubCommand(String command, String description, Permission requiredPermission) {
            this.command = command;
            this.description = description;
            this.requiredPermission = requiredPermission;
        }
        
        public String getCommand() { return command; }
        public String getDescription() { return description; }
        public Permission getRequiredPermission() { return requiredPermission; }
    }
    
    /**
     * 权限级别枚举
     */
    public enum Permission {
        USER(0, "普通用户"),
        MOD_ADMIN(2, "MOD管理员"), 
        ADMIN(4, "服务器管理员");
        
        private final int level;
        private final String displayName;
        
        Permission(int level, String displayName) {
            this.level = level;
            this.displayName = displayName;
        }
        
        public int getLevel() { return level; }
        public String getDisplayName() { return displayName; }
        
        public boolean hasPermission(Permission required) {
            return this.level >= required.level;
        }
    }
    
    /**
     * 命令帮助信息生成器
     */
    public static class HelpGenerator {
        
        /**
         * 生成主命令帮助信息
         */
        public static String generateMainHelp() {
            StringBuilder help = new StringBuilder();
            help.append("§b=== Ausuka.Ai 助手命令帮助 ===§r\n");
            help.append("§a/ai chat§r - 进入AI对话模式\n");
            help.append("§a/ai exit§r - 退出AI对话模式\n");
            help.append("§a/ai new§r - 开始新的对话会话\n");
            help.append("§c/ai admin§r - 管理员专用功能 §7(需要管理员权限)§r\n");
            help.append("§7使用 /ai <子命令> 获取详细帮助§r");
            return help.toString();
        }
        
        /**
         * 生成管理员命令帮助信息
         */
        public static String generateAdminHelp() {
            StringBuilder help = new StringBuilder();
            help.append("§c=== 管理员专用命令 ===§r\n");
            
            for (AdminSubCommand cmd : AdminSubCommand.values()) {
                help.append(String.format("§c/ai admin %s§r - %s\n", 
                    cmd.getCommand(), cmd.getDescription()));
            }
            
            help.append("\n§e自动消息系统：§r\n");
            for (AutoMsgSubCommand cmd : AutoMsgSubCommand.values()) {
                help.append(String.format("§e/ai admin auto-msg %s§r - %s\n", 
                    cmd.getCommand(), cmd.getDescription()));
            }
            
            return help.toString();
        }
        
        /**
         * 根据权限生成适当的帮助信息
         */
        public static String generateContextualHelp(Permission userPermission) {
            StringBuilder help = new StringBuilder();
            help.append(generateMainHelp());
            
            if (userPermission.hasPermission(Permission.ADMIN)) {
                help.append("\n\n").append(generateAdminHelp());
            }
            
            return help.toString();
        }
    }
    
    /**
     * 命令路径构建器
     */
    public static class CommandPath {
        private final List<String> path;
        
        public CommandPath(String... commands) {
            this.path = Arrays.asList(commands);
        }
        
        public String getFullCommand() {
            return "/" + String.join(" ", path);
        }
        
        public List<String> getPath() {
            return path;
        }
        
        public static CommandPath of(String... commands) {
            return new CommandPath(commands);
        }
        
        // 预定义的常用命令路径
        public static final CommandPath AI_CHAT = CommandPath.of("ai", "chat");
        public static final CommandPath AI_EXIT = CommandPath.of("ai", "exit");
        public static final CommandPath AI_NEW = CommandPath.of("ai", "new");
        public static final CommandPath AI_ADMIN = CommandPath.of("ai", "admin");
        public static final CommandPath AI_ADMIN_AUTO_MSG_TOGGLE = CommandPath.of("ai", "admin", "auto-msg", "toggle");
        public static final CommandPath AI_ADMIN_AUTO_MSG_STATUS = CommandPath.of("ai", "admin", "auto-msg", "status");
    }
    
    /**
     * 命令验证器
     */
    public static class CommandValidator {
        
        /**
         * 验证命令路径是否有效
         */
        public static boolean isValidCommandPath(List<String> commandPath) {
            if (commandPath.isEmpty()) return false;
            
            // 验证主命令
            if (!commandPath.get(0).equals(MainCommand.AI.getCommand())) {
                return false;
            }
            
            if (commandPath.size() == 1) return true;
            
            // 验证子命令
            String subCommand = commandPath.get(1);
            for (AiSubCommand cmd : AiSubCommand.values()) {
                if (cmd.getCommand().equals(subCommand)) {
                    return validateSubCommandPath(commandPath.subList(1, commandPath.size()));
                }
            }
            
            return false;
        }
        
        private static boolean validateSubCommandPath(List<String> subCommandPath) {
            if (subCommandPath.isEmpty()) return false;
            
            String firstSub = subCommandPath.get(0);
            
            // 检查是否是有效的一级子命令
            for (AiSubCommand cmd : AiSubCommand.values()) {
                if (cmd.getCommand().equals(firstSub)) {
                    if (cmd == AiSubCommand.ADMIN) {
                        return validateAdminCommandPath(subCommandPath.subList(1, subCommandPath.size()));
                    }
                    return subCommandPath.size() == 1;
                }
            }
            
            return false;
        }
        
        private static boolean validateAdminCommandPath(List<String> adminCommandPath) {
            if (adminCommandPath.isEmpty()) return true; // /ai admin 本身是有效的
            
            String adminSubCommand = adminCommandPath.get(0);
            
            for (AdminSubCommand cmd : AdminSubCommand.values()) {
                if (cmd.getCommand().equals(adminSubCommand)) {
                    if (cmd == AdminSubCommand.AUTO_MSG && adminCommandPath.size() > 1) {
                        return validateAutoMsgCommandPath(adminCommandPath.subList(1, adminCommandPath.size()));
                    }
                    return adminCommandPath.size() == 1;
                }
            }
            
            return false;
        }
        
        private static boolean validateAutoMsgCommandPath(List<String> autoMsgCommandPath) {
            if (autoMsgCommandPath.isEmpty()) return true;
            
            String autoMsgSubCommand = autoMsgCommandPath.get(0);
            
            for (AutoMsgSubCommand cmd : AutoMsgSubCommand.values()) {
                if (cmd.getCommand().equals(autoMsgSubCommand)) {
                    return autoMsgCommandPath.size() == 1;
                }
            }
            
            return false;
        }
    }
}