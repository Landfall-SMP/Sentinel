# Sentinel

**Sentinel** is a Velocity plugin that securely links Minecraft accounts to Discord accounts. It integrates with a Discord bot to verify players before allowing login, helping to prevent impersonation and aiding in connecting players together.

## Commands
    /link <code>
    Link your Minecraft account to your Discord account.

    /whois [discord: @user | minecraft: username]
    Look up linked account info.

## Requirements

- Java 17+
- Velocity Proxy (tested on 3.4.0-SNAPSHOT)
- MySQL or MariaDB

## Setup

1. **Install the plugin**  
   Place the shaded JAR into your `plugins/` folder on your Velocity proxy.

2. **Configure MySQL**  
   On first run, `plugins/sentinel/config.json` will be created. Fill in your database credentials and Discord token:
   ```json
   {
     "mysql": {
       "host": "localhost",
       "port": 3306,
       "database": "sentinel",
       "username": "sentinel_user",
       "password": "password"
     },
     "discord_token": "your_discord_bot_token"
   }
   ```

