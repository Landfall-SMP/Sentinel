package com.confect1on.sentinel.config;

public class SentinelConfig {
    public MySQL mysql = new MySQL();
    public Discord discord = new Discord();

    public static class MySQL {
        public String host = "localhost";
        public int port = 3306;
        public String database = "sentinel";
        public String username = "sentinel_user";
        public String password = "change_me";
    }

    public static class Discord {
        public String token = "";
    }
}
