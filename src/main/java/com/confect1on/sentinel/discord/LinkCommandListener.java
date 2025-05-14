package com.confect1on.sentinel.discord;

import com.confect1on.sentinel.db.DatabaseManager;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;

import java.util.UUID;

public class LinkCommandListener extends ListenerAdapter {
    private final DatabaseManager db;
    private final Logger logger;

    private final SlashCommandData commandData = Commands
            .slash("link", "Link your Minecraft account")
            .addOption(
                    net.dv8tion.jda.api.interactions.commands.OptionType.STRING,
                    "code", "Your link code", true
            );

    public LinkCommandListener(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    public SlashCommandData getCommandData() {
        return commandData;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent evt) {
        if (!"link".equals(evt.getName())) return;

        String code = evt.getOption("code").getAsString();

        // block til defer is sent to make sure we make it within 3s
        InteractionHook hook;
        try {
            hook = evt.deferReply(true).complete();
        } catch (Exception ex) {
            logger.error("❌ Failed to defer /link interaction", ex);
            return;
        }

        // nowwww do the db work
        UUID uuid = db.claimPending(code);
        if (uuid == null) {
            hook.sendMessage("❌ Invalid or expired code.").queue();
            return;
        }
        if (!db.addLink(uuid, evt.getUser().getId())) {
            hook.sendMessage("❌ This Discord account is already linked!").queue();
            return;
        }

        hook.sendMessage("✅ Your account has been linked!").queue();
        logger.info("[Sentinel] Linked Minecraft {} ↔ Discord {}", uuid, evt.getUser().getId());
    }
}
