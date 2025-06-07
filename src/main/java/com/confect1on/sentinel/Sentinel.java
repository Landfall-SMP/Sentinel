package com.confect1on.sentinel;

import com.confect1on.sentinel.config.ConfigLoader;
import com.confect1on.sentinel.db.DatabaseManager;
import com.confect1on.sentinel.discord.DiscordManager;
import com.confect1on.sentinel.listener.LoginListener;
import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import javax.security.auth.login.LoginException;
import java.nio.file.Path;

@Plugin(id = "sentinel", name = "Sentinel", version = BuildConstants.VERSION)
public class Sentinel {
    @Inject private Logger logger;
    @Inject private ProxyServer server;
    @Inject @DataDirectory private Path dataDirectory;

    private DatabaseManager database;
    private DiscordManager discord;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("🔌 Starting Sentinel…");
        var config = ConfigLoader.loadConfig(dataDirectory, logger);

        try {
            database = new DatabaseManager(config.mysql, logger);
        } catch (RuntimeException e) {
            logger.error("💥 Disabled: DB connection failed.");
            return;
        }

        // start Discord if we have a token
        if (config.discord.token != null && !config.discord.token.isBlank()) {
            try {
                discord = new DiscordManager(database, config.discord.token, config.discord.linkedRole, config.discord.quarantineRole, config.discord.staffRoles, server, config, logger);
                discord.start();
            } catch (LoginException e) {
                logger.error("❌ Failed to start Discord bot", e);
            }
        } else {
            logger.warn("❌ Failed to start Discord bot, no token provided!");
        }

        // register login guard (after Discord is initialized)
        server.getEventManager().register(this, new LoginListener(database, config, discord, logger));

        logger.info("✅ Sentinel up and running.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (discord != null) discord.shutdown();
        if (database != null) {
            database.close();
            logger.info("🔒 Database pool closed.");
        }
    }
}
