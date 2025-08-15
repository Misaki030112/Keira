package com.hinadt.ai.prompt;

import net.minecraft.server.network.ServerPlayerEntity;

public class PromptComposer {

    public String compose(ServerPlayerEntity player,
                          String message,
                          String detailedContext,
                          String conversationContext,
                          String permissionContext,
                          boolean isAdmin,
                          String memoryContext,
                          String serverContext,
                          String toolAvailability) {
        String playerName = player.getName().getString();
        return String.format("""
            # Ausuka.ai 智能助手 - Minecraft服务器AI伴侣

            你是一个高度智能的Minecraft AI助手。你的任务是理解玩家需求并使用合适的工具来帮助他们。

            %s

            ## 当前情况分析

            ### 玩家信息
            **玩家姓名**: %s
            **权限状态**: %s
            **详细状态**: 
            %s

            ### 玩家记忆与偏好
            %s

            ### 服务器状态
            %s

            ### 玩家请求
            **原始消息**: "%s"

            ## 行为指导原则与权限
            %s
            
            ## 工具可用性
            %s
            """,
            conversationContext,
            playerName,
            isAdmin ? "管理员" : "普通用户",
            detailedContext,
            memoryContext,
            serverContext,
            message,
            permissionContext,
            toolAvailability
        );
    }
}
