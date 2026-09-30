/**
 * Small inline SVG set. Inline SVG keeps the project free of image downloads
 * and lets the icons follow the CSS colour variables.
 */
public class Icons {

    private static final String OPEN =
        "<svg viewBox=\"0 0 48 48\" role=\"img\" focusable=\"false\" aria-hidden=\"true\" "
      + "fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" "
      + "stroke-linecap=\"round\" stroke-linejoin=\"round\">";

    public static String forCategory(String category) {
        if (category == null) return svg("box");
        String c = category.toLowerCase();
        if (c.startsWith("book")) return svg("book");
        if (c.startsWith("electronic")) return svg("laptop");
        if (c.startsWith("hostel")) return svg("lamp");
        if (c.startsWith("sport")) return svg("cycle");
        if (c.startsWith("accessor")) return svg("bag");
        return svg("box");
    }

    public static String svg(String kind) {
        String body;
        if ("book".equals(kind)) {
            body = "<path d=\"M8 10a3 3 0 0 1 3-3h13v34H11a3 3 0 0 1-3-3z\"/>"
                 + "<path d=\"M40 10a3 3 0 0 0-3-3H24v34h13a3 3 0 0 0 3-3z\"/>"
                 + "<path d=\"M14 15h6M14 21h6M28 15h6M28 21h6\"/>";
        } else if ("laptop".equals(kind)) {
            body = "<rect x=\"10\" y=\"11\" width=\"28\" height=\"19\" rx=\"2\"/>"
                 + "<path d=\"M6 35h36l-3 4H9z\"/><path d=\"M15 17h10\"/>";
        } else if ("headphones".equals(kind)) {
            body = "<path d=\"M10 29v-5a14 14 0 0 1 28 0v5\"/>"
                 + "<rect x=\"6\" y=\"27\" width=\"8\" height=\"12\" rx=\"3\"/>"
                 + "<rect x=\"34\" y=\"27\" width=\"8\" height=\"12\" rx=\"3\"/>";
        } else if ("cycle".equals(kind)) {
            body = "<circle cx=\"13\" cy=\"32\" r=\"8\"/><circle cx=\"35\" cy=\"32\" r=\"8\"/>"
                 + "<path d=\"M13 32l8-14h7l6 14M21 18h8M26 32h9\"/>";
        } else if ("bag".equals(kind)) {
            body = "<path d=\"M12 18a12 12 0 0 1 24 0v20a3 3 0 0 1-3 3H15a3 3 0 0 1-3-3z\"/>"
                 + "<path d=\"M19 18v-4a5 5 0 0 1 10 0v4M16 27h16\"/>";
        } else if ("calc".equals(kind)) {
            body = "<rect x=\"12\" y=\"6\" width=\"24\" height=\"36\" rx=\"3\"/>"
                 + "<rect x=\"17\" y=\"11\" width=\"14\" height=\"7\" rx=\"1\"/>"
                 + "<path d=\"M18 25h2M23 25h2M28 25h2M18 31h2M23 31h2M28 31h2M18 37h12\"/>";
        } else if ("lamp".equals(kind)) {
            body = "<path d=\"M16 41h16M24 41V25\"/><path d=\"M14 25h20l-5-11H19z\"/>"
                 + "<path d=\"M24 7v4M33 10l-2 3M15 10l2 3\"/>";
        } else if ("wallet".equals(kind)) {
            body = "<rect x=\"7\" y=\"12\" width=\"34\" height=\"24\" rx=\"4\"/>"
                 + "<path d=\"M7 20h34M33 26h4\"/>";
        } else if ("check".equals(kind)) {
            body = "<circle cx=\"24\" cy=\"24\" r=\"18\"/><path d=\"M16 24l6 6 11-12\"/>";
        } else {
            body = "<path d=\"M24 6l16 9v18l-16 9-16-9V15z\"/><path d=\"M8 15l16 9 16-9M24 24v18\"/>";
        }
        return OPEN + body + "</svg>";
    }
}
