import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.*;
import java.nio.file.*;
import java.sql.*;

/**
 * GET /product-image?file=...
 *
 * Serves product images stored outside the Tomcat web application.
 */
@WebServlet("/product-image")
public class ProductImageServlet extends HttpServlet {

    private static final String UPLOAD_DIR =
            "C:\\campus-marketplace-data\\uploads";

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        String fileName =
                request.getParameter("file");

        if (fileName == null ||
                fileName.trim().isEmpty()) {

            response.sendError(
                    HttpServletResponse.SC_NOT_FOUND
            );

            return;
        }

        /*
         * Do not trust a path supplied by the browser.
         * First verify that the filename exists in our database.
         */

        String storedFileName = null;

        try (Connection con =
                     DBConnection.getConnection();

             PreparedStatement ps =
                     con.prepareStatement(
                             "SELECT file_name " +
                             "FROM listing_images " +
                             "WHERE file_name = ?"
                     )) {

            ps.setString(
                    1,
                    fileName
            );

            try (ResultSet rs =
                         ps.executeQuery()) {

                if (rs.next()) {
                    storedFileName =
                            rs.getString("file_name");
                }
            }

        } catch (SQLException e) {

            System.err.println(
                    "[ProductImageServlet] " +
                    e.getMessage()
            );

            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );

            return;
        }

        if (storedFileName == null) {

            response.sendError(
                    HttpServletResponse.SC_NOT_FOUND
            );

            return;
        }

        /*
         * Resolve only the database-approved filename.
         */

        Path base =
                Paths.get(UPLOAD_DIR)
                        .toAbsolutePath()
                        .normalize();

        Path file =
                base.resolve(storedFileName)
                        .normalize();

        if (!file.startsWith(base)) {

            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN
            );

            return;
        }

        if (!Files.exists(file) ||
                !Files.isRegularFile(file)) {

            response.sendError(
                    HttpServletResponse.SC_NOT_FOUND
            );

            return;
        }

        String contentType =
                Files.probeContentType(file);

        if (contentType == null) {

            contentType =
                    "application/octet-stream";
        }

        if (!contentType.startsWith("image/")) {

            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN
            );

            return;
        }

        response.setContentType(
                contentType
        );

        response.setHeader(
                "Cache-Control",
                "public, max-age=86400"
        );

        response.setContentLengthLong(
                Files.size(file)
        );

        try (InputStream in =
                     Files.newInputStream(file);

             OutputStream out =
                     response.getOutputStream()) {

            byte[] buffer =
                    new byte[8192];

            int length;

            while ((length =
                    in.read(buffer)) != -1) {

                out.write(
                        buffer,
                        0,
                        length
                );
            }
        }
    }
}   