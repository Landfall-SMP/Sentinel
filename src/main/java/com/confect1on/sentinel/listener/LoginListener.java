package com.confect1on.sentinel.listener;

import com.confect1on.sentinel.db.DatabaseManager;
import com.confect1on.sentinel.config.SentinelConfig;
import com.confect1on.sentinel.discord.DiscordManager;
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
    private final DiscordManager discordManager;

    public LoginListener(DatabaseManager database, SentinelConfig config, DiscordManager discordManager, Logger logger) {
        this.database = database;
        this.config = config;
        this.discordManager = discordManager;
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
                String discordId = database.getDiscordId(uuid);
                
                // Check if Discord user is still in the server and handle cleanup
                if (discordManager != null && discordManager.getQuarantineChecker() != null) {
                    // This will check if the user is still in Discord and clean up if they left
                    boolean isStillInDiscord = isDiscordUserStillInServer(discordId);
                    
                    if (!isStillInDiscord) {
                        // User was kicked/left Discord, they're no longer linked
                        logger.info("🔗 {} ({}) was linked but Discord user {} is no longer in server. Generating new link code.", 
                            username, uuid, discordId);
                        
                        // Generate a new link code
                        String code = generateCode();
                        database.savePendingCode(uuid, code);
                        
                        event.setResult(ComponentResult.denied(
                            Component.text("Your Discord account is no longer linked.\n" +
                                "Use code §b" + code + "§r in Discord to link.")
                        ));
                        return;
                    }
                    
                    // Check for quarantine if they're still in Discord
                    if (discordManager.getQuarantineChecker().isQuarantined(discordId)) {
                        logger.debug("🚫 {} ({}) is quarantined. Denying login.", username, uuid);
                        event.setResult(ComponentResult.denied(
                                Component.text(config.discord.quarantineMessage)
                        ));
                        return;
                    }
                }
                
                // save the current username each login for quick lookup
                database.updateUsername(uuid, username);

                logger.debug("✅ {} ({}) is linked. Allowing login.", username, uuid);
                event.setResult(ComponentResult.allowed());
            } else {
                // Check if they were linked but removed due to leaving Discord
                String discordId = database.getDiscordId(uuid);
                if (discordId != null) {
                    // THIS SHOULD NEVER HAPPEN!
                    logger.warn("🔗 {} ({}) was linked but needs to relink.", username, uuid);
                    event.setResult(ComponentResult.denied(
                            Component.text("Your Discord account is no longer linked.\n" +
                                    "Please contact an administrator to relink your account.")
                    ));
                    return;
                }
                
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

    /**
     * Checks if a Discord user is still in the server.
     * If they're not, removes them from the database.
     */
    private boolean isDiscordUserStillInServer(String discordId) {
        if (discordId == null) return false;
        
        try {
            // Use the QuarantineChecker's method to check if user is still in Discord
            return discordManager.getQuarantineChecker().isUserStillInDiscord(discordId);
        } catch (Exception e) {
            logger.error("🔗 Error checking if Discord user {} is still in server", discordId, e);
            return true; // Default to assuming they're still there on error
        }
    }

    private String generateCode() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
