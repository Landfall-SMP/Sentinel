package com.confect1on.sentinel.discord;

import com.confect1on.sentinel.db.DatabaseManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.slf4j.Logger;

import javax.security.auth.login.LoginException;

public class DiscordManager {
    private final DatabaseManager db;
    private final Logger logger;
    private final String token;

    private final LinkCommandListener linkListener;
    private final WhoIsCommandListener whoisListener;

    private JDA jda;

    public DiscordManager(DatabaseManager db, String token, Logger logger) {
        this.db = db;
        this.token = token;
        this.logger = logger;

        this.linkListener = new LinkCommandListener(db, logger);
        this.whoisListener = new WhoIsCommandListener(db, logger);
    }

    public void start() throws LoginException {
        jda = JDABuilder.createDefault(token)
                .addEventListeners(linkListener, whoisListener)
                .build();

        // Register both /link and /whois commands
        jda.updateCommands()
                .addCommands(
                        linkListener.getCommandData(),
                        whoisListener.getCommandData()
                )
                .queue();

        logger.info("[Sentinel] Discord bot started with /link and /whois.");
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
            logger.info("[Sentinel] Discord bot shut down.");
        }
    }
}
