import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Single place where the JDBC settings live.
 *
 * Database : campus_marketplace  (MySQL running inside XAMPP)
 * User     : root
 * Password : empty (XAMPP default)
 */
public class DBConnection {

    private static final String URL =
            "jdbc:mysql://localhost:3306/campus_marketplace_final"
            + "?useSSL=false&allowPublicKeyRetrieval=true"
            + "&serverTimezone=Asia/Kolkata&useUnicode=true&characterEncoding=UTF-8";

    private static final String USER = "root";
    private static final String PASSWORD = "";   // XAMPP default: blank password

    /** Load the MySQL driver once when the class is first used. */
    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            // Printed to the Tomcat log only - never shown to the user.
            System.err.println("[DBConnection] MySQL driver not found. "
                    + "Copy mysql-connector-j-8.0.33.jar into WEB-INF/lib.");
            e.printStackTrace();
        }
    }

    /**
     * Opens a new connection. The caller must close it -
     * every servlet in this project uses try-with-resources.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** Small helper so servlets can show a friendly "database unavailable" page. */
    public static boolean isReachable() {
        try (Connection c = getConnection()) {
            return c != null && !c.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
