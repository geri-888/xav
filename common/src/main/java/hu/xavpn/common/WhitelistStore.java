package hu.xavpn.common;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

public final class WhitelistStore {
    private final XavDatabase database;

    public WhitelistStore(XavDatabase database) {
        this.database = database;
    }

    public synchronized void reload() {
    }

    public synchronized boolean contains(String name) {
        return database.isWhitelisted(name);
    }

    public synchronized boolean add(String name) throws IOException {
        return database.addWhitelist(name);
    }

    public synchronized boolean remove(String name) throws IOException {
        return database.removeWhitelist(name);
    }

    public synchronized Set<String> snapshot() {
        return Collections.unmodifiableSet(database.whitelistSnapshot());
    }
}
