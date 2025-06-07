package com.confect1on.sentinel.discord;

import com.confect1on.sentinel.db.DatabaseManager;
import com.confect1on.sentinel.db.LinkInfo;
import com.confect1on.sentinel.config.SentinelConfig;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.util.Optional;

public class QuarantineCommandListener extends ListenerAdapter {
    private final DatabaseManager db;
    private final Logger logger;
    private final String quarantineRoleId;
    private final String[] staffRoles;
    private final ProxyServer proxyServer;
    private final SentinelConfig config;

    private final SlashCommandData commandData = Commands
            .slash("quarantine", "Toggle quarantine role for a user")
            .addOption(OptionType.STRING, "user", "Minecraft username or Discord @mention", true);

    public QuarantineCommandListener(DatabaseManager db, String quarantineRoleId, String[] staffRoles, ProxyServer proxyServer, SentinelConfig config, Logger logger) {
        this.db = db;
        this.quarantineRoleId = quarantineRoleId;
        this.staffRoles = staffRoles;
        this.proxyServer = proxyServer;
        this.config = config;
        this.logger = logger;
    }

    public SlashCommandData getCommandData() {
        return commandData;
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!"quarantine".equals(event.getName())) return;

        // Check if quarantine is configured
        if (quarantineRoleId == null || quarantineRoleId.isBlank()) {
            event.reply("❌ Quarantine role is not configured.").setEphemeral(true).queue();
            return;
        }

        // Check if user has permission to use this command
        if (!hasStaffPermission(event)) {
            event.reply("❌ You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        var userOption = event.getOption("user");

        if (userOption == null) {
            event.reply("❌ User parameter is required.").setEphemeral(true).queue();
            return;
        }

        String userInput = userOption.getAsString();

        // Defer reply since we'll be doing database and Discord API calls
        event.deferReply().queue(hook -> {
            try {
                // Find the quarantine role
                Role quarantineRole = null;
                Guild targetGuild = null;

                for (Guild guild : event.getJDA().getGuilds()) {
                    Role role = guild.getRoleById(quarantineRoleId);
                    if (role != null) {
                        quarantineRole = role;
                        targetGuild = guild;
                        break;
                    }
                }

                if (quarantineRole == null || targetGuild == null) {
                    hook.sendMessage("❌ Quarantine role not found in any guild.").queue();
                    return;
                }

                // Parse user input - could be Discord mention or Minecraft username
                String discordId = null;
                String minecraftUsername = null;

                if (userInput.startsWith("<@") && userInput.endsWith(">")) {
                    // Discord mention
                    discordId = userInput.replaceAll("[<@!>]", "");
                } else {
                    // Assume Minecraft username
                    minecraftUsername = userInput;
                    
                    // Look up Discord ID from database
                    Optional<LinkInfo> linkInfo = db.findByUsername(minecraftUsername);
                    if (linkInfo.isPresent()) {
                        discordId = linkInfo.get().discordId();
                    } else {
                        hook.sendMessage("❌ No linked account found for Minecraft username: " + minecraftUsername).queue();
                        return;
                    }
                }

                // Make discordId final for use in lambdas
                final String finalDiscordId = discordId;

                // Find the member in Discord
                Member targetMember;
                try {
                    targetMember = targetGuild.retrieveMemberById(finalDiscordId).complete();
                } catch (Exception e) {
                    hook.sendMessage("❌ User not found in Discord server.").queue();
                    return;
                }

                // Toggle the quarantine role
                boolean hasQuarantineRole = targetMember.getRoles().contains(quarantineRole);
                
                if (hasQuarantineRole) {
                    // Remove quarantine role
                    targetGuild.removeRoleFromMember(targetMember, quarantineRole).queue(
                        success -> {
                            hook.sendMessage("✅ Removed quarantine role from " + targetMember.getEffectiveName()).queue();
                            logger.info("✅ {} removed quarantine role from {}", event.getUser().getAsTag(), targetMember.getEffectiveName());
                        },
                        error -> {
                            hook.sendMessage("❌ Failed to remove quarantine role: " + error.getMessage()).queue();
                            logger.error("🚫 Failed to remove quarantine role from {}", targetMember.getEffectiveName(), error);
                        }
                    );
                } else {
                    // Add quarantine role
                    targetGuild.addRoleToMember(targetMember, quarantineRole).queue(
                        success -> {
                            hook.sendMessage("🚫 Added quarantine role to " + targetMember.getEffectiveName()).queue();
                            logger.info("🚫 {} added quarantine role to {}", event.getUser().getAsTag(), targetMember.getEffectiveName());
                            
                            // Kick the player if they're currently online
                            kickPlayerIfOnline(finalDiscordId, targetMember.getEffectiveName());
                        },
                        error -> {
                            hook.sendMessage("❌ Failed to add quarantine role: " + error.getMessage()).queue();
                            logger.error("🚫 Failed to add quarantine role to {}", targetMember.getEffectiveName(), error);
                        }
                    );
                }

            } catch (Exception e) {
                hook.sendMessage("❌ An error occurred while processing the command.").queue();
                logger.error("🚫 Error in quarantine command", e);
            }
        });
    }

    private boolean hasStaffPermission(SlashCommandInteractionEvent event) {
        // If no staff roles are configured, fail shut
        if (staffRoles == null || staffRoles.length == 0) {
            return false;
        }
        
        // Get guild and member with null checks
        Guild guild = event.getGuild();
        if (guild == null) {
            return false;
        }
        
        Member member = event.getMember();
        if (member == null) {
            return false;
        }
        
        // Check if the member has any of the staff roles
        for (String staffRoleId : staffRoles) {
            if (staffRoleId != null && !staffRoleId.isBlank()) {
                Role staffRole = guild.getRoleById(staffRoleId);
                if (staffRole != null && member.getRoles().contains(staffRole)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Kicks a player from the proxy if they are currently online.
     * This is called when a quarantine role is added to ensure immediate enforcement.
     */
    private void kickPlayerIfOnline(String discordId, String discordUsername) {
        try {
            // Get the UUID from the Discord ID
            Optional<LinkInfo> linkInfo = db.findByDiscordId(discordId);
            if (linkInfo.isEmpty()) {
                logger.debug("🚫 No linked account found for Discord ID {} when trying to kick", discordId);
                return;
            }
            
            // Check if the player is currently online
            Optional<Player> onlinePlayer = proxyServer.getPlayer(linkInfo.get().uuid());
            if (onlinePlayer.isPresent()) {
                Player player = onlinePlayer.get();
                // Use the configured quarantine message
                player.disconnect(Component.text(config.discord.quarantineMessage));
                logger.info("🚫 Kicked player {} ({}) from proxy due to quarantine", player.getUsername(), linkInfo.get().uuid());
            } else {
                logger.debug("🚫 Player {} ({}) is not currently online, no kick needed", discordUsername, linkInfo.get().uuid());
            }
        } catch (Exception e) {
            logger.error("🚫 Error while trying to kick quarantined player with Discord ID {}", discordId, e);
        }
    }
} 