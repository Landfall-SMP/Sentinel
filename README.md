# Sentinel

**Sentinel** is a Velocity plugin that securely links Minecraft accounts to Discord accounts. It integrates with a Discord bot to verify players before allowing login, helping to prevent impersonation and aiding in connecting players together. Sentinel also provides quarantine functionality to temporarily restrict access for problematic players.

## Commands

### Discord Commands
    /link <code>
    Link your Minecraft account to your Discord account.

    /whois [discord: @user | minecraft: username]
    Look up linked account info.

    /quarantine <user>
    Toggle quarantine role for a user (Minecraft username or Discord @mention).
    Requires staff role permissions. Automatically kicks online players when quarantined.

### In-Game Commands
    /link
    Generates a unique code to link your Minecraft account to Discord.

## Features

- **Account Linking**: Secure verification system linking Minecraft accounts to Discord
- **Login Protection**: Prevents unlinked accounts from joining (with configurable bypass servers)
- **Role Management**: Automatic Discord role assignment for linked players
- **Quarantine System**: Staff can quarantine problematic players, preventing login and kicking online players
- **Automatic Cleanup**: Removes database entries for users who leave Discord
- **Rate Limiting**: Respects Discord API limits with intelligent rate limiting

## Requirements

- Java 17+
- Velocity Proxy (tested on 3.4.0-SNAPSHOT)
- MySQL or MariaDB

## Setup

1. **Install the plugin**  
   Place the shaded JAR into your `plugins/` folder on your Velocity proxy.

2. **Configure MySQL and Discord**  
   On first run, `plugins/sentinel/config.json` will be created. Fill in your database credentials, Discord token, and optionally configure quarantine and staff roles:
   ```json
   {
     "mysql": {
       "host": "localhost",
       "port": 3306,
       "database": "sentinel",
       "username": "sentinel_user",
       "password": "password"
     },
     "discord": {
       "token": "your_discord_bot_token",
       "linkedRole": "123456789012345678",
       "quarantineRole": "987654321098765432",
       "quarantineMessage": "Your account has been quarantined. Contact an administrator.",
       "staffRoles": ["111111111111111111", "222222222222222222"]
     },
     "bypassServers": {
       "servers": ["lobby", "auth"]
     }
   }
   ```

### Configuration Options

- **`bypassServers`**: Server names that bypass Discord verification requirement. Useful for lobbies or auth servers where unverified players should be allowed.

- **`linkedRole`**: (Optional) Discord role ID automatically assigned to all linked players. When configured:
  - All existing linked accounts receive the role on bot startup (rate-limited)
  - New accounts that link immediately receive the role
  - Role synchronization spreads over ~20 minutes for large databases

- **`quarantineRole`**: (Optional) Discord role ID for quarantined players. When configured:
  - Players with this role are denied login
  - Staff can use `/quarantine` command to toggle this role
  - Online players are immediately kicked when quarantined

- **`quarantineMessage`**: Message shown to quarantined players when they try to join or are kicked

- **`staffRoles`**: Array of Discord role IDs that can use the `/quarantine` command
