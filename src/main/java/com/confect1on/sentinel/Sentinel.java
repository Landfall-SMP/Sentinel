package com.confect1on.sentinel;

import com.confect1on.sentinel.config.ConfigLoader;
import com.confect1on.sentinel.config.SentinelConfig;
import com.confect1on.sentinel.db.DatabaseManager;
import com.confect1on.sentinel.listener.LoginListener;
import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(id = "sentinel", name = "Sentinel", version = BuildConstants.VERSION)
public class Sentinel {

    @Inject private Logger logger;
    @Inject private ProxyServer server;
    @Inject @DataDirectory private Path dataDirectory;

    private DatabaseManager database;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("🔌 Starting Sentinel…");

        // 1. Load config (creates default config.json if missing)
        SentinelConfig config = ConfigLoader.loadConfig(dataDirectory, logger);

        // 2. Initialize DB (will throw if it cannot connect)
        try {
            database = new DatabaseManager(config.mysql, logger);
        } catch (RuntimeException e) {
            logger.error("💥 Sentinel disabled due to DB connection failure.");
            return;
        }

        // 3. Register our login listener
        server.getEventManager().register(this, new LoginListener(database, logger));
        logger.info("✅ Sentinel initialized and listening for logins.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        // Cleanly close Hikari pool
        if (database != null) {
            database.close();
            logger.info("🔒 Sentinel database connection closed.");
        }
    }
}
