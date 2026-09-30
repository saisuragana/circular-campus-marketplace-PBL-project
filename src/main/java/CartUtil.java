import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Cart rules for the project.
 *
 * A cart row belongs to exactly one owner:
 *   - a guest       -> student_id IS NULL and session_id = HttpSession id
 *   - a logged user -> student_id = the student's id
 *
 * When a guest logs in, the guest rows are merged into the student's cart
 * (GuestCartMigration) so nothing is lost.
 */
public class CartUtil {

    /** @return the logged-in student id, or null for a guest. */
    public static Integer studentId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        return (Integer) session.getAttribute("studentId");
    }

    /** Session id used as the guest cart key. */
    public static String guestKey(HttpServletRequest request) {
        return request.getSession(true).getId();
    }

    /** Total number of units currently in the visitor's cart (0 if anything fails). */
    public static int countItems(HttpServletRequest request) {
        Integer sid = studentId(request);
        String sql = sid != null
                ? "SELECT COALESCE(SUM(quantity),0) FROM cart_items WHERE student_id = ?"
                : "SELECT COALESCE(SUM(quantity),0) FROM cart_items WHERE student_id IS NULL AND session_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (sid != null) ps.setInt(1, sid); else ps.setString(1, guestKey(request));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[CartUtil] countItems failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Adds a listing to the cart, or increases the quantity if it is already there.
     * @return the quantity now held for that listing
     */
    public static int addOrIncrease(Connection con, HttpServletRequest request,
                                    int listingId, int qty) throws SQLException {
        Integer sid = studentId(request);
        String find = sid != null
                ? "SELECT cart_id, quantity FROM cart_items WHERE student_id = ? AND listing_id = ?"
                : "SELECT cart_id, quantity FROM cart_items WHERE student_id IS NULL AND session_id = ? AND listing_id = ?";
        try (PreparedStatement ps = con.prepareStatement(find)) {
            if (sid != null) ps.setInt(1, sid); else ps.setString(1, guestKey(request));
            ps.setInt(2, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int cartId = rs.getInt("cart_id");
                    int newQty = rs.getInt("quantity") + qty;
                    try (PreparedStatement up = con.prepareStatement(
                            "UPDATE cart_items SET quantity = ? WHERE cart_id = ?")) {
                        up.setInt(1, newQty);
                        up.setInt(2, cartId);
                        up.executeUpdate();
                    }
                    return newQty;
                }
            }
        }
        try (PreparedStatement ins = con.prepareStatement(
                "INSERT INTO cart_items (session_id, student_id, listing_id, quantity) VALUES (?,?,?,?)")) {
            ins.setString(1, request.getSession(true).getId());
            if (sid != null) ins.setInt(2, sid); else ins.setNull(2, java.sql.Types.INTEGER);
            ins.setInt(3, listingId);
            ins.setInt(4, qty);
            ins.executeUpdate();
        }
        return qty;
    }

    /**
     * Moves a guest cart into a student cart right after login.
     * Rows already present for the same listing are merged, not duplicated.
     *
     * @return how many guest rows were moved or merged
     */
    public static int migrateGuestCart(Connection con, String guestSessionId, int studentId)
            throws SQLException {
        int moved = 0;

        // 1. merge quantities where the student already has the same listing
        String mergeSelect =
            "SELECT g.cart_id AS gid, g.listing_id, g.quantity AS gqty, s.cart_id AS sid, s.quantity AS sqty "
          + "FROM cart_items g JOIN cart_items s "
          + "  ON s.listing_id = g.listing_id AND s.student_id = ? "
          + "WHERE g.student_id IS NULL AND g.session_id = ?";
        try (PreparedStatement ps = con.prepareStatement(mergeSelect)) {
            ps.setInt(1, studentId);
            ps.setString(2, guestSessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int keepId = rs.getInt("sid");
                    int total  = rs.getInt("sqty") + rs.getInt("gqty");
                    int dropId = rs.getInt("gid");
                    try (PreparedStatement up = con.prepareStatement(
                            "UPDATE cart_items SET quantity = ? WHERE cart_id = ?");
                         PreparedStatement del = con.prepareStatement(
                            "DELETE FROM cart_items WHERE cart_id = ?")) {
                        up.setInt(1, total);
                        up.setInt(2, keepId);
                        up.executeUpdate();
                        del.setInt(1, dropId);
                        del.executeUpdate();
                    }
                    moved++;
                }
            }
        }

        // 2. the remaining guest rows simply change owner
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE cart_items SET student_id = ?, session_id = ? "
              + "WHERE student_id IS NULL AND session_id = ?")) {
            ps.setInt(1, studentId);
            ps.setString(2, guestSessionId);
            ps.setString(3, guestSessionId);
            moved += ps.executeUpdate();
        }
        return moved;
    }
}
