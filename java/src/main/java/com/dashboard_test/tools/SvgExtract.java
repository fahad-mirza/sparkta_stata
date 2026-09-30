package com.dashboard_test.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * SvgExtract (v3.6.0-s9p): pulls the SVG the page wrote into &lt;textarea id="spkSvgOut"&gt;
 * (headless --dump-dom of ...html?export=svg) and writes it as a standalone .svg file.
 * Called from the ado via javacall after the headless browser run. Never touches Stata data.
 *   args: [0] dumped-DOM html path  [1] output .svg path
 * Return: 0 ok; 1 no SVG found (prints the page's error textarea if present); 2 io error.
 */
public final class SvgExtract {
    public static int execute(String[] args) {
        if (args == null || args.length < 2) { System.out.println("SvgExtract: need <dump.html> <out.svg>"); return 2; }
        try {
            String html = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
            String svg = between(html, "id=\"spkSvgOut\"", "</textarea>");
            if (svg == null) svg = between(html, "id='spkSvgOut'", "</textarea>");
            if (svg == null) {
                String err = between(html, "id=\"spkSvgErr\"", "</textarea>");
                System.out.println("SvgExtract: no SVG in dump" + (err == null ? "" : ": " + unescape(err).trim()));
                return 1;
            }
            svg = unescape(svg);
            int i = svg.indexOf("<svg"); if (i > 0) svg = svg.substring(i);
            Files.write(Paths.get(args[1]), ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + svg.trim() + "\n").getBytes(StandardCharsets.UTF_8));
            return 0;
        } catch (Exception e) { System.out.println("SvgExtract: " + e.getMessage()); return 2; }
    }
    private static String between(String s, String a, String b) {
        int i = s.indexOf(a); if (i < 0) return null;
        int gt = s.indexOf('>', i); if (gt < 0) return null;
        int j = s.indexOf(b, gt); if (j < 0) return null;
        return s.substring(gt + 1, j);
    }
    private static String unescape(String t) {
        return t.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
    }
    public static void main(String[] a) { System.exit(execute(a)); }
}
