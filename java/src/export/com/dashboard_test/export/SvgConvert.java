package com.dashboard_test.export;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * SvgConvert (v3.6.0-s9y) -- the FAST export path for sparkta saveas().
 *   One headless-browser run produces the SVG (the page's own exporter); this class then
 *   makes PNG at any scale and a vector PDF from that SVG in pure Java, in well under a second.
 *
 *   args: [0] in.svg  [1] out.(png|pdf)  [2] scale (PNG only; 1 = 96 dpi, 3 = ~288 dpi)  [3] bg (PNG: "white" | "transparent")
 *   Return 0 ok, 1 usage, 2 error (message on stdout so Stata shows it).
 *
 *   Libraries (fetched by fetch_js_libs.bat into java/lib, compiled by build.bat into sparkta-export.jar):
 *     jsvg (MIT)                 SVG -> Java2D renderer
 *     pdfbox + fontbox (Apache)  PDF writer
 *     pdfbox-graphics2d (Apache) Java2D -> PDF content (keeps vectors and text as text)
 *   Sparkta never touches Stata data here; the class is self-contained.
 */
public final class SvgConvert {

    public static int execute(String[] args) {
        if (args == null || args.length < 2) { System.out.println("SvgConvert: need <in.svg> <out.png|pdf> [scale] [white|transparent]"); return 1; }
        try {
            File in = new File(args[0]); File out = new File(args[1]);
            double scale = args.length > 2 && !args[2].trim().isEmpty() ? Double.parseDouble(args[2].trim()) : 2.0;
            boolean transparent = args.length > 3 && args[3].trim().equalsIgnoreCase("transparent");
            // jsvg 2.x: load(URL); size() returns a FloatSize whose package moved between
            // releases -- use var so this file does not name it
            SVGDocument doc = new SVGLoader().load(in.toURI().toURL());
            if (doc == null) { System.out.println("SvgConvert: could not parse " + in); return 2; }
            var sz = doc.size();
            float w = sz.width, h = sz.height;
            String ext = out.getName().toLowerCase();
            if (ext.endsWith(".png")) return png(doc, w, h, scale, transparent, out);
            if (ext.endsWith(".pdf")) return pdf(doc, w, h, out);
            System.out.println("SvgConvert: unsupported output " + out.getName()); return 1;
        } catch (Exception e) {
            System.out.println("SvgConvert: " + e.getClass().getSimpleName() + ": " + e.getMessage()); return 2;
        }
    }

    private static int png(SVGDocument doc, float w, float h, double scale, boolean transparent, File out) throws Exception {
        int pw = (int) Math.ceil(w * scale), ph = (int) Math.ceil(h * scale);
        BufferedImage img = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        quality(g);
        if (!transparent) { g.setColor(Color.WHITE); g.fillRect(0, 0, pw, ph); }
        g.scale(scale, scale);
        doc.render(null, g);
        g.dispose();
        ImageIO.write(img, "png", out);
        System.out.println("  saveas: " + out.getName() + " (" + pw + "x" + ph + ", scale " + scale + ")");
        return 0;
    }

    private static int pdf(SVGDocument doc, float w, float h, File out) throws Exception {
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(w, h));
            pdf.addPage(page);
            PdfBoxGraphics2D g = new PdfBoxGraphics2D(pdf, w, h);
            quality(g);
            doc.render(null, g);
            g.dispose();
            PDFormXObject form = g.getXFormObject();
            try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) { cs.drawForm(form); }
            pdf.save(out);
        }
        System.out.println("  saveas: " + out.getName() + " (vector PDF, " + Math.round(w) + "x" + Math.round(h) + " pt)");
        return 0;
    }

    private static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    public static void main(String[] a) { System.exit(execute(a)); }
}
