package com.confect1on.sentinel.listener;

import com.confect1on.sentinel.db.DatabaseManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.ResultedEvent.ComponentResult;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.UUID;

public class LoginListener {

    private final DatabaseManager database;
    private final Logger logger;

    public LoginListener(DatabaseManager database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        UUID uuid = event.getPlayer().getGameProfile().getId();
        String username = event.getPlayer().getUsername();

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
