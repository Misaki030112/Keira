package com.hinadt.persistence;

import com.hinadt.AusukaAiMod;
import com.hinadt.persistence.mapper.PlayerConnectionMapper;
import com.hinadt.persistence.mapper.PlayerProfileMapper;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * 玩家连接观测记录器
 * - 记录每次加入的语言(locale)、IP等信息
 * - 聚合更新 player_profiles，并新增一条 player_connections 历史
 */
public final class PlayerTelemetryRecorder {
    private PlayerTelemetryRecorder() {}

    public static void recordJoin(ServerPlayerEntity player, ClientConnection connection, ConnectedClientData clientData) {
        try (var session = MyBatisSupport.getFactory().openSession(true)) {
            String uuid = player.getUuidAsString();
            String name = player.getGameProfile().getName();
            String ip = extractIp(connection);
            String locale = extractLocale(clientData);

            var profileMapper = session.getMapper(PlayerProfileMapper.class);
            int updated = profileMapper.touchProfile(uuid, name, ip, locale);
            if (updated == 0) {
                profileMapper.insertProfile(uuid, name, ip, locale);
            }

            var connMapper = session.getMapper(PlayerConnectionMapper.class);
            connMapper.insertConnection(uuid, name, ip, locale);

            AusukaAiMod.LOGGER.debug("玩家加入记录完成: uuid={}, name={}, ip={}, locale={}", uuid, name, ip, locale);
        } catch (Exception e) {
            AusukaAiMod.LOGGER.warn("记录玩家加入信息失败: " + e.getMessage(), e);
        }
    }

    private static String extractIp(ClientConnection connection) {
        try {
            SocketAddress addr = connection.getAddress();
            if (addr instanceof InetSocketAddress isa) {
                var inet = isa.getAddress();
                return inet != null ? inet.getHostAddress() : isa.getHostString();
            }
            return String.valueOf(addr);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String extractLocale(ConnectedClientData clientData) {
        // 兼容不同版本字段/方法命名，使用反射尽力获取
        try {
            // 直接在 clientData 上查找 language()
            var langMethod = clientData.getClass().getMethod("language");
            Object v = langMethod.invoke(clientData);
            if (v instanceof String s && !s.isEmpty()) return s;
        } catch (Throwable ignore) {}

        // 尝试通过嵌套的 client information 对象获取
        String[] infoMethodCandidates = new String[]{
                "clientInfo", "clientInformation", "getClientInformation", "getClientSettings", "settings"
        };
        for (String mName : infoMethodCandidates) {
            try {
                var m = clientData.getClass().getMethod(mName);
                Object info = m.invoke(clientData);
                if (info == null) continue;
                try {
                    var lm = info.getClass().getMethod("language");
                    Object lv = lm.invoke(info);
                    if (lv instanceof String s && !s.isEmpty()) return s;
                } catch (Throwable ignore) {}
                try {
                    var lm = info.getClass().getMethod("getLanguage");
                    Object lv = lm.invoke(info);
                    if (lv instanceof String s && !s.isEmpty()) return s;
                } catch (Throwable ignore) {}
            } catch (Throwable ignore) {}
        }
        return "unknown";
    }
}
