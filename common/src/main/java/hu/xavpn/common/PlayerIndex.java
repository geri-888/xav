package hu.xavpn.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PlayerIndex {
    private final XavDatabase database;

    public PlayerIndex(XavDatabase database) {
        this.database = database;
    }

    public synchronized void record(String name, String ip) {
        database.recordPlayer(name, ip);
    }

    public synchronized Entry get(String name) {
        return database.getPlayer(name);
    }

    public synchronized List<Entry> findByIp(String ip) {
        if (ip == null) {
            return Collections.emptyList();
        }
        return new ArrayList<Entry>(database.findPlayersByIp(ip));
    }

    public static final class Entry {
        private final String name;
        private final String ip;

        public Entry(String name, String ip) {
            this.name = name;
            this.ip = ip;
        }

        public String getName() {
            return name;
        }

        public String getIp() {
            return ip;
        }
    }
}
