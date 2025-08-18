package com.hinadt.persistence;

import com.hinadt.AusukaAiMod;
import com.hinadt.persistence.mapper.PlayerConnectionMapper;
import com.hinadt.persistence.mapper.PlayerProfileMapper;
import com.hinadt.util.PlayerLocales;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Player telemetry recorder.
 * - Records language (locale) and IP when a player joins.
 * - Upserts player_profiles and appends a record in player_connections.
 *
 * Notes on locale resolution:
 * - Do not reflectively probe ConnectedClientData. Use PlayerLocales.code(player)
 *   which centralizes compatibility and defaults to en_us.
 */
public final class PlayerTelemetryRecorder {
    private PlayerTelemetryRecorder() {}

    public static void recordJoin(ServerPlayerEntity player, ClientConnection connection, ConnectedClientData clientData) {
        try (var session = MyBatisSupport.getFactory().openSession(true)) {
            String uuid = player.getUuidAsString();
            String name = player.getGameProfile().getName();
            String ip = extractIp(connection);
            String locale = extractLocale(player);

            var profileMapper = session.getMapper(PlayerProfileMapper.class);
            int updated = profileMapper.touchProfile(uuid, name, ip, locale);
            if (updated == 0) {
                profileMapper.insertProfile(uuid, name, ip, locale);
            }

            var connMapper = session.getMapper(PlayerConnectionMapper.class);
            connMapper.insertConnection(uuid, name, ip, locale);

            AusukaAiMod.LOGGER.debug("Recorded player join: uuid={}, name={}, ip={}, locale={}", uuid, name, ip, locale);
        } catch (Exception e) {
            AusukaAiMod.LOGGER.warn("Failed to record player join: " + e.getMessage(), e);
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

    private static String extractLocale(ServerPlayerEntity player) {
        try {
            return PlayerLocales.code(player);
        } catch (Exception e) {
            return PlayerLocales.serverCode();
        }
    }
}
