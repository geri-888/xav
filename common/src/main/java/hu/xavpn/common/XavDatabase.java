package hu.xavpn.common;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class XavDatabase {
    private final File dataFolder;
    private final XavConfig config;
    private Connection connection;
    private boolean sqlite;

    public XavDatabase(File dataFolder, XavConfig config) {
        this.dataFolder = dataFolder;
        this.config = config;
    }

    public synchronized void open() throws SQLException, IOException {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IOException("Nem sikerult letrehozni: " + dataFolder.getAbsolutePath());
        }
        String type = config.getStorageType() == null ? "h2" : config.getStorageType().trim().toLowerCase(Locale.ROOT);
        sqlite = "sqlite".equals(type) || "sqlite3".equals(type);
        if (sqlite) {
            loadDriver("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + new File(dataFolder, "xavpn.sqlite3").getAbsolutePath());
        } else {
            loadDriver("org.h2.Driver");
            String path = new File(dataFolder, "xavpn").getAbsolutePath().replace("\\", "/");
            connection = DriverManager.getConnection("jdbc:h2:file:" + path + ";MODE=MySQL;DATABASE_TO_UPPER=false", "sa", "");
        }
        createTables();
        migrateLegacyFiles();
    }

    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            connection = null;
        }
    }

    public synchronized boolean isWhitelisted(String name) {
        PreparedStatement statement = null;
        ResultSet result = null;
        try {
            statement = connection.prepareStatement("SELECT name FROM whitelist WHERE name = ?");
            statement.setString(1, normalize(name));
            result = statement.executeQuery();
            return result.next();
        } catch (SQLException ignored) {
            return false;
        } finally {
            closeQuietly(result);
            closeQuietly(statement);
        }
    }

    public synchronized boolean addWhitelist(String name) throws IOException {
        String normalized = normalize(name);
        if (isWhitelisted(normalized)) {
            return false;
        }
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement("INSERT INTO whitelist(name) VALUES(?)");
            statement.setString(1, normalized);
            statement.executeUpdate();
            return true;
        } catch (SQLException exception) {
            throw new IOException("Nem sikerult menteni a whitelistet", exception);
        } finally {
            closeQuietly(statement);
        }
    }

    public synchronized boolean removeWhitelist(String name) throws IOException {
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement("DELETE FROM whitelist WHERE name = ?");
            statement.setString(1, normalize(name));
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IOException("Nem sikerult menteni a whitelistet", exception);
        } finally {
            closeQuietly(statement);
        }
    }

    public synchronized Set<String> whitelistSnapshot() {
        Set<String> names = new LinkedHashSet<String>();
        Statement statement = null;
        ResultSet result = null;
        try {
            statement = connection.createStatement();
            result = statement.executeQuery("SELECT name FROM whitelist ORDER BY name");
            while (result.next()) {
                names.add(result.getString("name"));
            }
        } catch (SQLException ignored) {
        } finally {
            closeQuietly(result);
            closeQuietly(statement);
        }
        return names;
    }

    public synchronized void recordPlayer(String name, String ip) {
        if (name == null || ip == null || name.trim().isEmpty() || ip.trim().isEmpty()) {
            return;
        }
        PreparedStatement delete = null;
        PreparedStatement insert = null;
        try {
            delete = connection.prepareStatement("DELETE FROM player_ips WHERE player_key = ?");
            delete.setString(1, normalize(name));
            delete.executeUpdate();
            insert = connection.prepareStatement("INSERT INTO player_ips(player_key, name, ip, updated_at) VALUES(?, ?, ?, ?)");
            insert.setString(1, normalize(name));
            insert.setString(2, name);
            insert.setString(3, ip);
            insert.setLong(4, System.currentTimeMillis());
            insert.executeUpdate();
        } catch (SQLException ignored) {
        } finally {
            closeQuietly(insert);
            closeQuietly(delete);
        }
    }

    public synchronized PlayerIndex.Entry getPlayer(String name) {
        PreparedStatement statement = null;
        ResultSet result = null;
        try {
            statement = connection.prepareStatement("SELECT name, ip FROM player_ips WHERE player_key = ?");
            statement.setString(1, normalize(name));
            result = statement.executeQuery();
            if (result.next()) {
                return new PlayerIndex.Entry(result.getString("name"), result.getString("ip"));
            }
        } catch (SQLException ignored) {
        } finally {
            closeQuietly(result);
            closeQuietly(statement);
        }
        return null;
    }

    public synchronized List<PlayerIndex.Entry> findPlayersByIp(String ip) {
        List<PlayerIndex.Entry> entries = new ArrayList<PlayerIndex.Entry>();
        PreparedStatement statement = null;
        ResultSet result = null;
        try {
            statement = connection.prepareStatement("SELECT name, ip FROM player_ips WHERE ip = ? ORDER BY name");
            statement.setString(1, ip);
            result = statement.executeQuery();
            while (result.next()) {
                entries.add(new PlayerIndex.Entry(result.getString("name"), result.getString("ip")));
            }
        } catch (SQLException ignored) {
        } finally {
            closeQuietly(result);
            closeQuietly(statement);
        }
        return entries;
    }

    private void createTables() throws SQLException {
        Statement statement = connection.createStatement();
        try {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS whitelist (name VARCHAR(64) PRIMARY KEY)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_ips (player_key VARCHAR(64) PRIMARY KEY, name VARCHAR(64) NOT NULL, ip VARCHAR(64) NOT NULL, updated_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_player_ips_ip ON player_ips(ip)");
        } finally {
            statement.close();
        }
    }

    private void migrateLegacyFiles() throws IOException {
        File marker = new File(dataFolder, ".db-migrated");
        if (marker.exists()) {
            return;
        }

        File whitelist = new File(dataFolder, "whitelist.txt");
        if (whitelist.exists()) {
            for (String line : FileUtil.readLines(whitelist)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    addWhitelist(trimmed);
                }
            }
        }

        File players = new File(dataFolder, "players.properties");
        if (players.exists()) {
            java.util.Properties properties = new java.util.Properties();
            java.io.FileInputStream input = new java.io.FileInputStream(players);
            try {
                properties.load(input);
            } finally {
                input.close();
            }
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key, "");
                int separator = value.indexOf('|');
                if (separator > 0) {
                    recordPlayer(value.substring(0, separator), value.substring(separator + 1));
                }
            }
        }

        FileUtil.writeUtf8(marker, "Migrated legacy whitelist.txt and players.properties into database.\n");
    }

    private static void loadDriver(String className) throws SQLException {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new SQLException("Hianyzik az adatbazis driver: " + className, exception);
        }
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }
}
