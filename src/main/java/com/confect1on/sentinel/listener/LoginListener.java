package com.confect1on.sentinel.listener;

import com.confect1on.sentinel.db.DatabaseManager;
import com.confect1on.sentinel.config.SentinelConfig;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.ResultedEvent.ComponentResult;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.UUID;

public class LoginListener {

    private final DatabaseManager database;
    private final Logger logger;
    private final SentinelConfig config;

    public LoginListener(DatabaseManager database, SentinelConfig config, Logger logger) {
        this.database = database;
        this.config = config;
        this.logger = logger;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        UUID uuid = event.getPlayer().getGameProfile().getId();
        String username = event.getPlayer().getUsername();
        
        // Get the virtual host they're connecting through
        String virtualHost = event.getPlayer().getVirtualHost()
            .map(host -> host.getHostString())
            .orElse("");

        // Check if this is a bypass server based on the virtual host
        if (virtualHost != null && !virtualHost.isEmpty()) {
            for (String server : config.bypassServers.servers) {
                if (virtualHost.toLowerCase().contains(server.toLowerCase())) {
                    logger.info("✅ {} ({}) connecting through bypass virtual host {}. Allowing login.", 
                        username, uuid, virtualHost);
                    event.setResult(ComponentResult.allowed());
                    return;
                }
            }
        }

        try {
            if (database.isLinked(uuid)) {
                // save the current username each login for quick lookup
                database.updateUsername(uuid, username);

                logger.info("✅ {} ({}) is linked. Allowing login.", username, uuid);
                event.setResult(ComponentResult.allowed());
            } else {
                // generate & rotate the code
                String code = generateCode();
                database.savePendingCode(uuid, code);

                logger.info("❌ {} ({}) is not linked. Generated code: {}", username, uuid, code);
                event.setResult(ComponentResult.denied(
                        Component.text("This Minecraft account is not linked.\n" +
                                "Use code §b" + code + "§r in Discord to link.")
                ));
            }
        } catch (Exception e) {
            logger.error("⚠️ Error during login check for {} ({})", username, uuid, e);
            event.setResult(ComponentResult.denied(
                    Component.text("A server error occurred. Try again later.")
            ));
        }
    }

    private String generateCode() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
