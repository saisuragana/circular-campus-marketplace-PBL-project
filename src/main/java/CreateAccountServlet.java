import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.sql.*;

/**
 * POST /create-account
 *
 * Only Woxsen University institutional email addresses are allowed.
 * Server-side validation, duplicate email check, then an INSERT into students.
 * New accounts start with a demo wallet balance of 1000.00.
 */
@WebServlet("/create-account")
public class CreateAccountServlet extends HttpServlet {

    private static final String COLLEGE_EMAIL_DOMAIN = "@woxsen.edu.in";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.sendRedirect("create-account.html");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        request.setCharacterEncoding("UTF-8");

        String name = value(request, "name");
        String email = value(request, "email").toLowerCase();
        String password = request.getParameter("password");
        String confirm = request.getParameter("confirmPassword");

        // Name validation
        if (name.length() < 2) {
            back(response, "Enter your full name.", name, email);
            return;
        }

        // Basic email format validation
        if (!email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            back(response, "Enter a valid email address.", name, email);
            return;
        }

        // Woxsen institutional email validation
        if (!email.endsWith(COLLEGE_EMAIL_DOMAIN)) {
            back(response,
                    "Only Woxsen University email addresses ending with @woxsen.edu.in are allowed.",
                    name,
                    email);
            return;
        }

        // Password validation
        if (password == null || password.length() < 6) {
            back(response, "Use a password of at least 6 characters.", name, email);
            return;
        }

        // Confirm password
        if (!password.equals(confirm)) {
            back(response, "The two passwords do not match.", name, email);
            return;
        }

        try (Connection con = DBConnection.getConnection()) {

            // Check duplicate email
            try (PreparedStatement check = con.prepareStatement(
                    "SELECT student_id FROM students WHERE email = ?")) {

                check.setString(1, email);

                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        back(response,
                                "An account already uses that email. Try logging in.",
                                name,
                                email);
                        return;
                    }
                }
            }

            // Create account
            try (PreparedStatement ins = con.prepareStatement(
                    "INSERT INTO students "
                  + "(name, email, wallet_balance, password, verification_status) "
                  + "VALUES (?,?,?,?,?)")) {

                ins.setString(1, name);
                ins.setString(2, email);
                ins.setBigDecimal(3, new java.math.BigDecimal("1000.00"));
                ins.setString(4, PasswordUtil.hash(password));
                ins.setString(5, "Verified");

                ins.executeUpdate();
            }

            response.sendRedirect(
                    "login.html?created=1&email="
                    + URLEncoder.encode(email, "UTF-8")
            );

        } catch (SQLException e) {

            System.err.println("[CreateAccountServlet] " + e.getMessage());

            back(response,
                    "The database is not reachable. Start MySQL in XAMPP and try again.",
                    name,
                    email);
        }
    }

    private String value(HttpServletRequest r, String key) {
        String v = r.getParameter(key);
        return v == null ? "" : v.trim();
    }

    private void back(HttpServletResponse response,
                      String msg,
                      String name,
                      String email) throws IOException {

        response.sendRedirect(
                "create-account.html?error="
                + URLEncoder.encode(msg, "UTF-8")
                + "&name="
                + URLEncoder.encode(name, "UTF-8")
                + "&email="
                + URLEncoder.encode(email, "UTF-8")
        );
    }
}