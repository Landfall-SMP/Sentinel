package com.confect1on.sentinel.discord;

import com.confect1on.sentinel.db.DatabaseManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.slf4j.Logger;

/**
 * Handles quarantine role checking and automatic cleanup of users who left Discord.
 */
public class QuarantineChecker {
    private final DatabaseManager database;
    private final JDA jda;
    private final String quarantineRoleId;
    private final Logger logger;
    
    public QuarantineChecker(DatabaseManager database, JDA jda, String quarantineRoleId, Logger logger) {
        this.database = database;
        this.jda = jda;
        this.quarantineRoleId = quarantineRoleId;
        this.logger = logger;
    }
    
    /**
     * Checks if a Discord user has the quarantine role.
     * If the user has left Discord, removes them from the database.
     * 
     * @param discordId The Discord ID to check
     * @return true if the user has the quarantine role, false if they don't or left Discord
     */
    public boolean isQuarantined(String discordId) {
        if (quarantineRoleId == null || quarantineRoleId.isBlank()) {
            return false; // Quarantine disabled
        }
        
        try {
            // Find the quarantine role across all guilds
            Role quarantineRole = null;
            Guild targetGuild = null;
            
            for (Guild guild : jda.getGuilds()) {
                Role role = guild.getRoleById(quarantineRoleId);
                if (role != null) {
                    quarantineRole = role;
                    targetGuild = guild;
                    break;
                }
            }
            
            if (quarantineRole == null || targetGuild == null) {
                logger.warn("🚫 Quarantine role with ID {} not found in any guild", quarantineRoleId);
                return false;
            }
            
            // Try to find the member in the guild
            try {
                Member member = targetGuild.retrieveMemberById(discordId).complete();
                
                // Check if they have the quarantine role
                boolean hasQuarantineRole = member.getRoles().contains(quarantineRole);
                if (hasQuarantineRole) {
                    logger.debug("🚫 User {} is quarantined in guild {}", member.getEffectiveName(), targetGuild.getName());
                }
                return hasQuarantineRole;
                
            } catch (Exception e) {
                // Member not found - they left Discord, remove from database
                boolean removed = database.removeLinkByDiscordId(discordId);
                if (removed) {
                    logger.info("🔗 Removed user {} from database - no longer in Discord server", discordId);
                }
                return false; // Not quarantined because they're not even in Discord
            }
            
        } catch (Exception e) {
            logger.error("🚫 Error checking quarantine status for Discord ID {}", discordId, e);
            return false; // Default to not quarantined on error
        }
    }
    
    /**
     * Checks if a Discord user is still in the server (any guild the bot is in).
     * If the user has left Discord, removes them from the database.
     * 
     * @param discordId The Discord ID to check
     * @return true if the user is still in Discord, false if they left
     */
    public boolean isUserStillInDiscord(String discordId) {
        if (discordId == null) {
            return false;
        }
        
        try {
            // Check all guilds the bot is in
            for (Guild guild : jda.getGuilds()) {
                try {
                    guild.retrieveMemberById(discordId).complete();
                    return true; // Found the user in this guild
                } catch (Exception e) {
                    // User not found in this guild, continue to next guild
                }
            }
            
            // User not found in any guild - remove from database
            boolean removed = database.removeLinkByDiscordId(discordId);
            if (removed) {
                logger.info("🔗 Removed user {} from database - no longer in Discord server", discordId);
            }
            return false;
            
        } catch (Exception e) {
            logger.error("🔗 Error checking if Discord user {} is still in server", discordId, e);
            return true; // Default to assuming they're still there on error
        }
    }
} 