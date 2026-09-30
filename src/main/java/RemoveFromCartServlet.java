import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.sql.*;

/**
 * POST /remove-from-cart
 *
 * Deletes one cart line. The WHERE clause also checks the owner, so a visitor
 * can never delete somebody else's cart row by guessing a cart_id.
 */
@WebServlet("/remove-from-cart")
public class RemoveFromCartServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.sendRedirect("cart");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        int cartId;
        try {
            cartId = Integer.parseInt(request.getParameter("cartId"));
        } catch (Exception e) {
            response.sendRedirect("cart");
            return;
        }

        Integer studentId = CartUtil.studentId(request);
        String sql = studentId != null
                ? "DELETE FROM cart_items WHERE cart_id = ? AND student_id = ?"
                : "DELETE FROM cart_items WHERE cart_id = ? AND student_id IS NULL AND session_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, cartId);
            if (studentId != null) ps.setInt(2, studentId);
            else ps.setString(2, CartUtil.guestKey(request));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[RemoveFromCartServlet] " + e.getMessage());
        }
        response.sendRedirect("cart");
    }
}
