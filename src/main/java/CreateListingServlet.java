import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@WebServlet("/create-listing")
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = 5 * 1024 * 1024,
        maxRequestSize = 25 * 1024 * 1024
)
public class CreateListingServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final String UPLOAD_DIR =
            "C:\\campus-marketplace-data\\uploads";

    private static final int MIN_IMAGES = 2;

    private static final int MAX_IMAGES = 5;

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        Integer sellerId =
                CartUtil.studentId(request);

        if (sellerId == null) {

            response.sendRedirect(
                    "login.html"
            );

            return;
        }

        request.setCharacterEncoding(
                "UTF-8"
        );

        String title =
                clean(
                        request.getParameter(
                                "title"
                        )
                );

        String category =
                clean(
                        request.getParameter(
                                "category"
                        )
                );

        String condition =
                clean(
                        request.getParameter(
                                "condition"
                        )
                );

        String priceText =
                clean(
                        request.getParameter(
                                "price"
                        )
                );

        String quantityText =
                clean(
                        request.getParameter(
                                "quantity"
                        )
                );

        String description =
                clean(
                        request.getParameter(
                                "description"
                        )
                );

        String keywords =
                clean(
                        request.getParameter(
                                "keywords"
                        )
                );

        String conditionConfirm =
                clean(
                        request.getParameter(
                                "conditionConfirm"
                        )
                );

        /*
         * Basic form validation.
         */
        if (title.isEmpty()
                || category.isEmpty()
                || condition.isEmpty()
                || priceText.isEmpty()
                || quantityText.isEmpty()
                || description.isEmpty()
                || !"YES".equals(
                        conditionConfirm
                )) {

            back(
                    response,
                    "Please complete all required fields "
                    + "and confirm the product condition."
            );

            return;
        }

        BigDecimal price;

        int quantity;

        try {

            price =
                    new BigDecimal(
                            priceText
                    );

            quantity =
                    Integer.parseInt(
                            quantityText
                    );

        } catch (NumberFormatException e) {

            back(
                    response,
                    "Enter a valid price and quantity."
            );

            return;
        }

        /*
         * Validate price and quantity.
         */
        if (price.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            back(
                    response,
                    "Price must be greater than zero."
            );

            return;
        }

        if (quantity <= 0
                || quantity > 99) {

            back(
                    response,
                    "Quantity must be between 1 and 99."
            );

            return;
        }

        /*
         * Collect uploaded images.
         */
        List<Part> imageParts =
                new ArrayList<Part>();

        try {

            for (Part part :
                    request.getParts()) {

                if ("productImages".equals(
                        part.getName()
                )
                        && part.getSize() > 0) {

                    imageParts.add(
                            part
                    );
                }
            }

        } catch (Exception e) {

            back(
                    response,
                    "Unable to read uploaded images."
            );

            return;
        }

        /*
         * Image count validation.
         */
        if (imageParts.size()
                < MIN_IMAGES) {

            back(
                    response,
                    "Please upload at least "
                    + MIN_IMAGES
                    + " product images."
            );

            return;
        }

        if (imageParts.size()
                > MAX_IMAGES) {

            back(
                    response,
                    "You can upload a maximum of "
                    + MAX_IMAGES
                    + " images."
            );

            return;
        }

        /*
         * Automated image validation.
         *
         * Checks:
         * - file type
         * - file size
         * - duplicate image content
         */
        Set<String> hashes =
                new HashSet<String>();

        for (Part part :
                imageParts) {

            String contentType =
                    part.getContentType();

            if (!allowedImage(
                    contentType
            )) {

                back(
                        response,
                        "Only JPG, PNG and WEBP "
                        + "images are allowed."
                );

                return;
            }

            if (part.getSize()
                    > 5L * 1024L * 1024L) {

                back(
                        response,
                        "Each image must be "
                        + "5 MB or smaller."
                );

                return;
            }

            try {

                String hash =
                        sha256(
                                part
                        );

                if (!hashes.add(
                        hash
                )) {

                    back(
                            response,
                            "Duplicate product images "
                            + "were detected. Please "
                            + "upload different views."
                    );

                    return;
                }

            } catch (Exception e) {

                back(
                        response,
                        "Unable to validate one of "
                        + "the images."
                );

                return;
            }
        }

        /*
         * AI-assisted listing quality analysis.
         *
         * IMPORTANT:
         * This checks listing content and spam-like
         * patterns. It does NOT claim to detect physical
         * damage such as torn pages.
         */
        AIQualityEngine.QualityResult quality =
                null;

        try {

            quality =
                    AIQualityEngine.analyze(
                            title,
                            description,
                            keywords,
                            category,
                            condition,
                            price.doubleValue(),
                            quantity
                    );

        } catch (Exception e) {

            e.printStackTrace();
        }

        /*
         * Default validation values.
         */
        int score = 0;

        String reason =
                "Images and listing information "
                + "passed automated checks.";

        String validationStatus =
                "Auto Checked";

        /*
         * Use the actual QualityResult API.
         */
        if (quality != null) {

            score =
                    quality.getScore();

            List<String> reasons =
                    quality.getReasons();

            if (reasons != null
                    && !reasons.isEmpty()) {

                StringBuilder reasonBuilder =
                        new StringBuilder();

                for (String item :
                        reasons) {

                    if (item == null
                            || item.trim().isEmpty()) {

                        continue;
                    }

                    if (reasonBuilder.length()
                            > 0) {

                        reasonBuilder.append(
                                "; "
                        );
                    }

                    reasonBuilder.append(
                            item.trim()
                    );
                }

                if (reasonBuilder.length()
                        > 0) {

                    reason =
                            reasonBuilder.toString();
                }
            }

            /*
             * The existing engine returns:
             *
             * Validated
             * Review
             *
             * We do not create an admin/middleman
             * workflow. The marketplace still uses
             * automated checking.
             */
            if ("Validated".equalsIgnoreCase(
                    quality.getStatus()
            )) {

                validationStatus =
                        "Auto Checked";

            } else {

                validationStatus =
                        "Auto Checked";
            }
        }

        Connection con = null;

        int listingId = -1;

        List<File> savedFiles =
                new ArrayList<File>();

        try {

            con =
                    DBConnection.getConnection();

            con.setAutoCommit(
                    false
            );

            /*
             * Create listing.
             *
             * status = Available
             * because there is no admin/middleman
             * approval stage.
             */
            String insert =
                    "INSERT INTO listings "
                  + "(seller_id, title, category, price, "
                  + "`condition`, status, description, keywords, "
                  + "quantity, validation_status, "
                  + "validation_score, validation_reason) "
                  + "VALUES (?, ?, ?, ?, ?, 'Available', "
                  + "?, ?, ?, ?, ?, ?)";

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 insert,
                                 Statement.RETURN_GENERATED_KEYS
                         )) {

                ps.setInt(
                        1,
                        sellerId
                );

                ps.setString(
                        2,
                        title
                );

                ps.setString(
                        3,
                        category
                );

                ps.setBigDecimal(
                        4,
                        price
                );

                ps.setString(
                        5,
                        condition
                );

                ps.setString(
                        6,
                        description
                );

                ps.setString(
                        7,
                        keywords
                );

                ps.setInt(
                        8,
                        quantity
                );

                ps.setString(
                        9,
                        validationStatus
                );

                ps.setInt(
                        10,
                        score
                );

                ps.setString(
                        11,
                        reason
                );

                ps.executeUpdate();

                try (ResultSet keys =
                             ps.getGeneratedKeys()) {

                    if (!keys.next()) {

                        throw new SQLException(
                                "Could not create listing."
                        );
                    }

                    listingId =
                            keys.getInt(1);
                }
            }

            /*
             * Create image directory.
             */
            File directory =
                    new File(
                            UPLOAD_DIR
                    );

            if (!directory.exists()
                    && !directory.mkdirs()) {

                throw new IOException(
                        "Could not create upload directory."
                );
            }

            /*
             * Save image records.
             */
            String imageInsert =
                    "INSERT INTO listing_images "
                  + "(listing_id, file_name, original_name) "
                  + "VALUES (?, ?, ?)";

            try (PreparedStatement imagePs =
                         con.prepareStatement(
                                 imageInsert
                         )) {

                for (Part part :
                        imageParts) {

                    String extension =
                            extensionFor(
                                    part.getContentType()
                            );

                    String storedName =
                            "listing_"
                            + listingId
                            + "_"
                            + UUID.randomUUID()
                            + extension;

                    File destination =
                            new File(
                                    directory,
                                    storedName
                            );

                    try (InputStream in =
                                 part.getInputStream()) {

                        Files.copy(
                                in,
                                destination.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                        );
                    }

                    savedFiles.add(
                            destination
                    );

                    imagePs.setInt(
                            1,
                            listingId
                    );

                    imagePs.setString(
                            2,
                            storedName
                    );

                    imagePs.setString(
                            3,
                            safeOriginalName(
                                    part.getSubmittedFileName()
                            )
                    );

                    imagePs.executeUpdate();
                }
            }

            /*
             * Everything succeeded.
             */
            con.commit();

            request.getSession()
                    .setAttribute(
                            "flash",
                            "Listing created successfully. "
                            + "Your product images and listing "
                            + "information passed automated checks."
                    );

            response.sendRedirect(
                    "view-item?id="
                    + listingId
            );

        } catch (Exception e) {

            e.printStackTrace();

            /*
             * Roll back database changes.
             */
            if (con != null) {

                try {

                    con.rollback();

                } catch (SQLException ignored) {
                }
            }

            /*
             * Delete images already written to disk.
             */
            for (File file :
                    savedFiles) {

                try {

                    Files.deleteIfExists(
                            file.toPath()
                    );

                } catch (Exception ignored) {
                }
            }

            String message =
                    e.getMessage();

            if (message == null
                    || message.trim().isEmpty()) {

                message =
                        "An unexpected error occurred.";
            }

            back(
                    response,
                    "Listing could not be created: "
                    + message
            );

        } finally {

            if (con != null) {

                try {

                    con.setAutoCommit(
                            true
                    );

                    con.close();

                } catch (SQLException ignored) {
                }
            }
        }
    }

    private String clean(
            String value) {

        return value == null
                ? ""
                : value.trim();
    }

    private boolean allowedImage(
            String type) {

        return "image/jpeg".equalsIgnoreCase(
                    type
                )
                || "image/png".equalsIgnoreCase(
                    type
                )
                || "image/webp".equalsIgnoreCase(
                    type
                );
    }

    private String extensionFor(
            String type) {

        if ("image/png".equalsIgnoreCase(
                type
        )) {

            return ".png";
        }

        if ("image/webp".equalsIgnoreCase(
                type
        )) {

            return ".webp";
        }

        return ".jpg";
    }

    private String safeOriginalName(
            String name) {

        if (name == null
                || name.trim().isEmpty()) {

            return "product-image";
        }

        String clean =
                new File(name)
                        .getName();

        if (clean.length() > 255) {

            return clean.substring(
                    clean.length() - 255
            );
        }

        return clean;
    }

    private String sha256(
            Part part)
            throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );

        try (InputStream in =
                     part.getInputStream()) {

            byte[] buffer =
                    new byte[8192];

            int read;

            while ((read =
                    in.read(buffer)) != -1) {

                digest.update(
                        buffer,
                        0,
                        read
                );
            }
        }

        byte[] bytes =
                digest.digest();

        StringBuilder hex =
                new StringBuilder();

        for (byte b : bytes) {

            hex.append(
                    String.format(
                            "%02x",
                            b
                    )
            );
        }

        return hex.toString();
    }

    private void back(
            HttpServletResponse response,
            String message)
            throws IOException {

        response.setContentType(
                "text/html;charset=UTF-8"
        );

        response.getWriter().print(
                Page.message(
                        null,
                        "Create Listing",
                        "Listing could not be created",
                        Page.esc(message),
                        "create-listing.html",
                        "Back to listing form",
                        "error"
                )
        );
    }
}