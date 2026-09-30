package com.dashboard_test.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * HeadlessBrowser (v3.6.0-t1b): ONE headless Chromium per Stata session, driven over the
 * DevTools protocol with the JDK's own WebSocket client (no dependencies).
 *
 *   The first sparkta saveas() launches it (~2 s); every later one reuses it (~0.3 s):
 *   open tab -> navigate to file:///page.html?export=... -> Runtime.evaluate the page's own
 *   _spkChartToSvg() -> SVG string returned over the socket -> close tab. Stata's JVM keeps
 *   static state between javacalls, so the process handle survives; a shutdown hook closes
 *   the browser when Stata exits, and "sparkta, closebrowser" closes it on demand.
 *
 *   Entry points (javacall ... execute, args(...)):
 *     svg  <browser.exe> <page.html> <out.svg> [canvasId]     -> writes the SVG file
 *     png  <browser.exe> <page.html> <out.png> [scale] [scope] -> FULL-PAGE PNG (fix9d)
 *     close                                                    -> quits the browser
 *   Return 0 ok, 1 usage, 2 failure (message on System.out so Stata shows it). Falls back
 *   to the shell route in the ado when this returns non-zero.
 */
public final class HeadlessBrowser {
    private static Process proc;
    private static String  httpBase;      // http://127.0.0.1:<port>
    private static Path    userDataDir;
    private static final HttpClient http = HttpClient.newHttpClient();
    // t1f: idle timer -- the browser closes itself after this many ms without a saveas();
    // the next call relaunches (~2 s). Override with -DSPARKTA_BROWSER_IDLE_MS or the
    // SPARKTA_BROWSER_IDLE_MS environment variable; 0 = never (use closebrowser / exit).
    private static java.util.Timer idleTimer;
    private static java.util.TimerTask idleTask;
    // fix9v (Fahad's 2026-09-16 run: 50 s after a saveas() the JVM said "no session browser" yet an
    // msedge.exe tree of 7 processes, root pid 3180 with a DEAD parent, was still alive): the
    // process Java launches is a LAUNCHER that exits once the real browser is up (or on
    // Browser.close), so proc.isAlive() goes false and proc.toHandle().descendants() is EMPTY by
    // the time close() walks it -- the real browser was never in the kill list. Record the tree
    // while the launcher is still alive (right after the DevTools port file appears, and again at
    // every export) and kill THAT list in close(), whatever happened to the launcher.
    // Keyed by pid but the VALUE is the original ProcessHandle: a handle remembers its start time,
    // so isAlive() is false once that pid has been reused by an unrelated process (never kill those).
    private static final java.util.Map<Long, ProcessHandle> sessionTree = new java.util.LinkedHashMap<>();
    private static long   sessionStartMs = 0;
    private static String lastClose = "never";   // fix9v: shown by `procs`
    private static boolean hookAdded = false;
    /** fix9v: second net -- same executable as ours, started after the launch, not under a browser
     *  that already existed (the user's own). Catches the real browser when the launcher exited
     *  before trackTree() could walk it, or re-parented its child. */
    private static synchronized void trackByLaunch(String exe, java.util.Set<Long> preLaunch, long launchMs) {
        try {
            final String exeName = new File(exe).getName().toLowerCase(java.util.Locale.ROOT);
            ProcessHandle.allProcesses().forEach(h -> {
                try {
                    if (preLaunch.contains(h.pid()) || sessionTree.containsKey(h.pid())) return;
                    ProcessHandle.Info inf = h.info();
                    String cmd = inf.command().orElse("");
                    if (!new File(cmd).getName().toLowerCase(java.util.Locale.ROOT).equals(exeName)) return;
                    long st = inf.startInstant().map(java.time.Instant::toEpochMilli).orElse(Long.MAX_VALUE);
                    if (st < launchMs - 2000) return;
                    // walk up: a live ancestor that is a browser AND pre-dates the launch = the user's tree
                    for (java.util.Optional<ProcessHandle> pp = h.parent(); pp.isPresent(); pp = pp.get().parent()) {
                        ProcessHandle a = pp.get(); String ac = a.info().command().orElse("");
                        if (preLaunch.contains(a.pid()) && new File(ac).getName().toLowerCase(java.util.Locale.ROOT).equals(exeName)) return;
                    }
                    sessionTree.put(h.pid(), h);
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }
    /** fix9v: remember every process under the launcher (plus the launcher) -- call while it is alive. */
    private static synchronized void trackTree() {
        try {
            if (proc == null) return;
            ProcessHandle root = proc.toHandle();
            sessionTree.putIfAbsent(root.pid(), root);
            root.descendants().forEach(d -> sessionTree.putIfAbsent(d.pid(), d));
            // the real browser's own children (renderers, GPU) may hang off a handle we already hold
            for (ProcessHandle h : new java.util.ArrayList<>(sessionTree.values())) { try { if (h.isAlive()) h.descendants().forEach(d -> sessionTree.putIfAbsent(d.pid(), d)); } catch (Exception ignored) {} }
        } catch (Exception ignored) {}
    }
    private static long idleMs() {
        try { String v = System.getProperty("SPARKTA_BROWSER_IDLE_MS", System.getenv("SPARKTA_BROWSER_IDLE_MS")); if (v != null && !v.isEmpty()) return Long.parseLong(v.trim()); } catch (Exception ignored) {}
        // fix9q ([stated] Fahad: "after the exporting is complete, that window should close" -- on
        // his Windows the headless=new session browser is listed in Alt+Tab while it idles):
        // default idle 120 s -> 10 s. Exports inside one do-file are normally seconds apart, so
        // the ~0.3 s reuse is kept there; a longer gap costs one ~2 s relaunch. idle(#) raises it.
        return 10000L;
    }
    private static synchronized void touchIdle() {
        trackTree();   // fix9v: the real browser may have spawned more children since launch
        if (idleTask != null) { idleTask.cancel(); idleTask = null; }
        long ms = idleMs(); if (ms <= 0) return;
        if (idleTimer == null) idleTimer = new java.util.Timer("sparkta-browser-idle", true);   // daemon: never blocks Stata's exit
        idleTask = new java.util.TimerTask() { @Override public void run() { close("idle"); } };
        idleTimer.schedule(idleTask, ms);
    }

    /** t1d: write to Stata's console when running under Stata (System.out is dropped on non-zero returns). */
    static void say(String msg) {
        try { Class<?> k = Class.forName("com.stata.sfi.SFIToolkit"); k.getMethod("displayln", String.class).invoke(null, msg); }
        catch (Throwable t) { System.out.println(msg); }
    }

    public static int execute(String[] args) {
        try {
            if (args == null || args.length == 0) { say("HeadlessBrowser: svg <exe> <page> <out.svg> | png <exe> <page> <out.png> [scale] [scope] | close"); return 1; }
            sweepStrays();   // fix9s: end any sparkta browser left behind by an earlier session
            switch (args[0]) {
                case "close": close(); return 0;
                case "procs": {   // fix9t: diagnostic -- list browser-like processes with their command lines (no PowerShell needed)
                    say("HeadlessBrowser procs: this JVM pid " + ProcessHandle.current().pid() + (proc != null && proc.isAlive() ? ", session browser launcher pid " + proc.pid() : ", no session browser launcher"));
                    // fix9v: the state that decides whether a browser SHOULD be alive right now
                    boolean devtools = false; if (httpBase != null) { try { getJson(httpBase + "/json/version"); devtools = true; } catch (Exception ignored) {} }
                    long trackedAlive = 0; for (ProcessHandle h : sessionTree.values()) if (h.isAlive()) trackedAlive++;
                    say("  session: devtools " + (httpBase == null ? "none" : (devtools ? "reachable" : "unreachable")) + ", tracked pids " + sessionTree.size() + " (" + trackedAlive + " alive)"
                        + (sessionStartMs > 0 ? ", started " + (System.currentTimeMillis() - sessionStartMs) / 1000 + " s ago" : "") + ", idle timer " + (idleTask == null ? "none" : "pending") + ", last close: " + lastClose);
                    int[] n = {0, 0};   // fix9u: n[1] = processes whose command LINE was readable
                    ProcessHandle.allProcesses().forEach(h -> {
                        try {
                            ProcessHandle.Info inf = h.info();
                            String cmd = inf.command().orElse(""), cl = inf.commandLine().orElse("");
                            if (cl.isEmpty()) { String[] a = inf.arguments().orElse(null); cl = cmd + (a != null ? " " + String.join(" ", a) : ""); }
                            String low = (cmd + " " + cl).toLowerCase(java.util.Locale.ROOT);
                            if (!(low.contains("msedge") || low.contains("chrome") || low.contains("chromium") || low.contains("sparkta"))) return;
                            n[0]++; if (cl.length() > cmd.length()) n[1]++;
                            say("  pid " + h.pid() + " parent " + h.parent().map(pp -> String.valueOf(pp.pid())).orElse("?") + " started " + inf.startInstant().map(Object::toString).orElse("?")
                                + (cl.contains("--headless") ? " HEADLESS" : "") + (cl.contains("sparkta-browser-") || cl.contains("sparkta_run_") ? " SPARKTA" : "") + (sessionTree.containsKey(h.pid()) ? " SESSION" : "")
                                + "\n    " + (cl.length() > 400 ? cl.substring(0, 400) + " ..." : cl));
                        } catch (Exception ignored) {}
                    });
                    say("  " + n[0] + " browser-like process(es)");
                    // fix9u (Fahad's 2026-09-16 run: 67 processes, no command line on any): the JDK does
                    // not implement ProcessHandle.Info.commandLine()/arguments() on Windows (JDK-8176725),
                    // so HEADLESS / SPARKTA markers -- and sweepStrays() -- cannot see arguments there.
                    // close() kills the tree recorded at launch (fix9v) instead.
                    if (n[0] > 0 && n[1] == 0) say("  (no command lines available on this platform -- HEADLESS/SPARKTA markers and the orphan sweep cannot match here; close() kills by process tree instead)");
                    return 0;
                }
                case "idle": {   // t1h: set the idle timeout (seconds) for this session; 0 = never close on idle
                    if (args.length < 2) { say("HeadlessBrowser idle: need <seconds>"); return 1; }
                    System.setProperty("SPARKTA_BROWSER_IDLE_MS", String.valueOf(Math.round(Double.parseDouble(args[1].trim()) * 1000)));
                    touchIdle(); return 0;
                }
                case "run": {   // t1c: one-off headless command WITHOUT a console window (replaces shell on Windows)
                    if (args.length < 2) { say("HeadlessBrowser run: need <exe> [args...]"); return 1; }
                    java.util.List<String> cmd = new java.util.ArrayList<>(java.util.Arrays.asList(args).subList(1, args.length));
                    // fix9p (Fahad, test_release r_saveas_page "HeadlessBrowser run: timed out" while Edge
                    // was open): the one-off --print-to-pdf / --screenshot run had NO --user-data-dir, so
                    // it opened the DEFAULT profile; with a normal Edge/Chrome window already using that
                    // profile the new process waits on the profile lock (or hands the command to the
                    // running window and never prints) until the 90 s cap. The session browser has
                    // always used its own directory -- give every one-off run one too, and remove it after.
                    Path runDir = null;
                    if (cmd.stream().noneMatch(a -> a.startsWith("--user-data-dir="))) {
                        try { runDir = Files.createTempDirectory("sparkta_run_"); cmd.add(1, "--user-data-dir=" + runDir.toString()); } catch (Exception e) { runDir = null; }
                    }
                    ProcessBuilder pb = new ProcessBuilder(cmd); pb.redirectErrorStream(true); pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                    Process p = pb.start();
                    try {
                        if (!p.waitFor(90, TimeUnit.SECONDS)) { killTree(p); say("HeadlessBrowser run: timed out"); return 2; }
                        return p.exitValue() == 0 ? 0 : 2;
                    } finally { if (runDir != null) { try { Thread.sleep(150); } catch (InterruptedException ignored) {} deleteTree(runDir.toFile()); } }
                }
                case "runcapture": {   // t1c: same, but stdout -> file (for --dump-dom)
                    if (args.length < 3) { say("HeadlessBrowser runcapture: need <outfile> <exe> [args...]"); return 1; }
                    java.util.List<String> cmd = new java.util.ArrayList<>(java.util.Arrays.asList(args).subList(2, args.length));
                    Path capDir = null;   // fix9p: own profile directory, as for "run"
                    if (cmd.stream().noneMatch(a -> a.startsWith("--user-data-dir="))) {
                        try { capDir = Files.createTempDirectory("sparkta_run_"); cmd.add(1, "--user-data-dir=" + capDir.toString()); } catch (Exception e) { capDir = null; }
                    }
                    ProcessBuilder pb = new ProcessBuilder(cmd); pb.redirectError(ProcessBuilder.Redirect.DISCARD); pb.redirectOutput(new File(args[1]));
                    Process p = pb.start();
                    try {
                        if (!p.waitFor(90, TimeUnit.SECONDS)) { killTree(p); say("HeadlessBrowser runcapture: timed out"); return 2; }
                        return p.exitValue() == 0 ? 0 : 2;
                    } finally { if (capDir != null) { try { Thread.sleep(150); } catch (InterruptedException ignored) {} deleteTree(capDir.toFile()); } }
                }
                case "png": {   // t2j fix9d: full-page PNG through the session browser (replaces --screenshot,
                                // which captured the 1100x700 window only and cropped tall pages: the
                                // post-estimation elements key, the lower by() panels). Scope = chart|page|table
                                // (the page's own ?print= layout, canvases swapped for their SVG).
                    if (args.length < 4) { say("HeadlessBrowser png: need <exe> <page.html> <out.png> [scale] [scope]"); return 1; }
                    long t0 = System.currentTimeMillis();
                    ensureRunning(args[1]);
                    double scale = 2; if (args.length > 4 && !args[4].isEmpty()) { try { scale = Double.parseDouble(args[4].trim()); } catch (NumberFormatException e) { scale = 2; } }
                    if (scale <= 0 || scale > 8) scale = 2;
                    String scope = (args.length > 5 && !args[5].isEmpty()) ? args[5].trim() : "chart";
                    String url = new File(args[2]).toURI().toString() + "?print=" + scope;
                    byte[] png = renderPng(url, scale);
                    touchIdle();
                    Files.write(Path.of(args[3]), png);
                    say("  saveas: png via session browser, full page at " + scale + "x (" + (System.currentTimeMillis() - t0) + " ms)");
                    return 0;
                }
                case "svg": {
                    if (args.length < 4) { say("HeadlessBrowser svg: need <exe> <page.html> <out.svg>"); return 1; }
                    long t0 = System.currentTimeMillis();
                    ensureRunning(args[1]);
                    String url = new File(args[2]).toURI().toString() + "?export=svg" + (args.length > 4 && !args[4].isEmpty() ? "&canvas=" + args[4] : "");
                    String svg = renderSvg(url);
                    touchIdle();
                    Files.write(Path.of(args[3]), ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + svg + "\n").getBytes(StandardCharsets.UTF_8));
                    say("  saveas: svg via session browser (" + (System.currentTimeMillis() - t0) + " ms)");
                    return 0;
                }
                default: say("HeadlessBrowser: unknown command [" + args[0] + "] (" + args.length + " args)"); return 1;
            }
        } catch (Throwable e) {   // t1c: Errors too (e.g. NoClassDefFoundError) -- never fail silently
            StringBuilder sb = new StringBuilder("HeadlessBrowser: " + e.getClass().getName() + ": " + e.getMessage());
            for (Throwable c = e.getCause(); c != null && sb.length() < 600; c = c.getCause()) sb.append(" <- ").append(c.getClass().getSimpleName()).append(": ").append(c.getMessage());
            StackTraceElement[] st = e.getStackTrace(); if (st != null && st.length > 0) sb.append(" @ ").append(st[0]);
            say(sb.toString());
            try { close(); } catch (Throwable ignored) {}
            throw new RuntimeException(sb.toString());   // t1d: an uncaught throwable is always shown by Stata
        }
    }

    // ---- lifecycle -------------------------------------------------------------------
    private static synchronized void ensureRunning(String exe) throws Exception {
        if (proc != null && proc.isAlive() && httpBase != null) {
            try { getJson(httpBase + "/json/version"); return; } catch (Exception e) { close(); }
        }
        userDataDir = Files.createTempDirectory("sparkta-browser-");
        say("  saveas: starting session browser: " + exe);
        // fix9v: snapshot every pid that exists BEFORE the launch -- a browser process of the same
        // exe that appears after it, and does not hang off a pre-existing browser (the user's own
        // Edge/Chrome), is ours even if the launcher that spawned it has already exited
        java.util.Set<Long> preLaunch = new java.util.HashSet<>();
        try { ProcessHandle.allProcesses().forEach(h -> preLaunch.add(h.pid())); } catch (Exception ignored) {}
        long launchMs = System.currentTimeMillis();
        ProcessBuilder pb = new ProcessBuilder(exe,
            "--headless=new", "--disable-gpu", "--no-sandbox", "--hide-scrollbars", "--log-level=3",
            "--no-first-run", "--no-default-browser-check", "--disable-extensions", "--disable-background-networking",
            "--window-size=1100,700", "--remote-debugging-port=0", "--user-data-dir=" + userDataDir.toString(),
            "about:blank");
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        proc = pb.start();
        // Chrome writes the chosen port to DevToolsActivePort inside the profile dir
        Path portFile = userDataDir.resolve("DevToolsActivePort");
        long deadline = System.currentTimeMillis() + 15000;
        while (!Files.exists(portFile) || Files.size(portFile) == 0) {
            if (!proc.isAlive()) throw new IOException("browser exited during start");
            if (System.currentTimeMillis() > deadline) throw new IOException("browser did not open a DevTools port in 15 s");
            Thread.sleep(50);
        }
        // t1i: on Windows the file can still be locked by the browser for a moment -- retry the read
        String port = null;
        for (int tries = 0; tries < 50 && port == null; tries++) {
            try { java.util.List<String> lines = Files.readAllLines(portFile); if (!lines.isEmpty() && !lines.get(0).trim().isEmpty()) port = lines.get(0).trim(); }
            catch (IOException locked) { /* "being used by another process" */ }
            if (port == null) Thread.sleep(100);
        }
        if (port == null) throw new IOException("could not read DevToolsActivePort (locked by the browser)");
        httpBase = "http://127.0.0.1:" + port;
        sessionTree.clear(); sessionStartMs = System.currentTimeMillis(); trackTree();   // fix9v: the launcher is alive here
        trackByLaunch(exe, preLaunch, launchMs);
        if (!hookAdded) { hookAdded = true; Runtime.getRuntime().addShutdownHook(new Thread(HeadlessBrowser::close)); }
    }

    static synchronized void close() { close("explicit"); }
    static synchronized void close(String why) {
        if (idleTask != null) { idleTask.cancel(); idleTask = null; }
        // fix9v: build the kill list FIRST -- after Browser.close the launcher is gone and its
        // descendants() would be empty (that is exactly how pid 3180 survived on Fahad's laptop)
        trackTree();
        java.util.List<ProcessHandle> victims = new java.util.ArrayList<>(sessionTree.values());
        // fix9r ([stated] Fahad: the Alt+Tab window was still there after more than 120 s): close
        // the browser through DevTools first (Browser.close = an orderly quit of the WHOLE browser,
        // every window and child process), then destroy the launched process, then any child
        // processes still alive (ProcessHandle.descendants -- on Windows the launched msedge.exe
        // can be a launcher whose children are the real browser, and Process.destroy() never
        // reaches them). /json/close alone was a no-op without a target id.
        try {
            if (httpBase != null) {
                String ver = getJson(httpBase + "/json/version"); String bws = jsonStr(ver, "webSocketDebuggerUrl");
                if (bws != null) { Ws b = new Ws(bws); try { b.send("{\"id\":99,\"method\":\"Browser.close\"}"); } finally { try { b.close(); } catch (Exception ignored) {} } }
            }
        } catch (Exception ignored) {}
        if (proc != null) {
            try { proc.waitFor(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            killTree(proc);
        }
        // fix9v: whatever Browser.close and the launcher kill left behind (real browser, GPU,
        // renderers) -- give the orderly quit up to 2 s, then destroy the rest, forcibly if needed
        int killed = 0;
        long until = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < until && victims.stream().anyMatch(ProcessHandle::isAlive)) { try { Thread.sleep(100); } catch (InterruptedException ignored) {} }
        for (ProcessHandle v : victims) { try { if (v.isAlive()) { killed++; if (!v.destroy()) v.destroyForcibly(); } } catch (Exception ignored) {} }
        try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        int left = 0; for (ProcessHandle v : victims) { try { if (v.isAlive()) { left++; v.destroyForcibly(); } } catch (Exception ignored) {} }
        lastClose = why + " at " + java.time.Instant.now().toString().substring(11, 19) + "Z, tracked " + victims.size() + ", force-killed " + killed + ", still alive after " + left;
        sessionTree.clear(); sessionStartMs = 0;
        proc = null; httpBase = null;
        sweepStrays();   // fix9s: anything of ours still alive after the orderly close
        if (userDataDir != null) { try { Thread.sleep(200); deleteTree(userDataDir.toFile()); } catch (Exception ignored) {} userDataDir = null; }
    }
    /** fix9s ([stated] Fahad: "background windows still open" even after the suite's explicit close,
     *  and after more than the idle period): those are ORPHANS -- headless browsers from earlier
     *  Stata sessions (a timed-out one-off run whose launcher was force-killed while its children
     *  lived on, a Stata that was killed, a JVM that never reached its shutdown hook). Every sparkta
     *  browser is launched with a profile directory named sparkta-browser-* or sparkta_run_*, so
     *  they can be told apart from the user's own Edge/Chrome by their command line. This sweep
     *  runs at the start of every HeadlessBrowser call and inside close(): any process whose
     *  command line names one of those directories and that is NOT the live session browser is
     *  destroyed, forcibly if needed. The user's own browser windows are never touched. */
    static void sweepStrays() {
        try {
            long self = proc != null && proc.isAlive() ? proc.pid() : -1L;
            java.util.Set<Long> keep = new java.util.HashSet<>();
            if (self > 0) { keep.add(self); try { proc.toHandle().descendants().forEach(d -> keep.add(d.pid())); } catch (Exception ignored) {} }
            keep.addAll(sessionTree.keySet());   // fix9v: the live session tree is closed by close(), not swept
            // never this JVM (Stata) or anything above it
            try { ProcessHandle me = ProcessHandle.current(); keep.add(me.pid()); for (java.util.Optional<ProcessHandle> pp = me.parent(); pp.isPresent(); pp = pp.get().parent()) keep.add(pp.get().pid()); } catch (Exception ignored) {}
            ProcessHandle.allProcesses().forEach(h -> {
                try {
                    if (keep.contains(h.pid())) return;
                    String cl = h.info().commandLine().orElse("");
                    if (cl.isEmpty()) { String[] a = h.info().arguments().orElse(null); if (a != null) cl = String.join(" ", a); }
                    // a sparkta browser and nothing else: headless, AND its profile flag names our directory
                    // (a shell or editor whose command line merely mentions the name must never be touched)
                    if (cl.indexOf("--headless") < 0) return;
                    int i = cl.indexOf("--user-data-dir="); if (i < 0) return;
                    String ud = cl.substring(i + 16, Math.min(cl.length(), i + 16 + 260));
                    if (ud.indexOf("sparkta-browser-") < 0 && ud.indexOf("sparkta_run_") < 0) return;
                    if (!h.destroy()) h.destroyForcibly();
                } catch (Exception ignored) {}
            });
        } catch (Throwable ignored) {}
    }
    /** fix9r: destroy a process and every descendant it still has (a browser is a process tree). */
    static void killTree(Process p) {
        try {
            java.util.List<ProcessHandle> kids = new java.util.ArrayList<>();
            try { p.toHandle().descendants().forEach(kids::add); } catch (Exception ignored) {}
            p.destroy();
            try { p.waitFor(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            if (p.isAlive()) p.destroyForcibly();
            for (ProcessHandle k : kids) { try { if (k.isAlive()) k.destroyForcibly(); } catch (Exception ignored) {} }
        } catch (Exception ignored) {}
    }
    private static void deleteTree(File f) { File[] kids = f.listFiles(); if (kids != null) for (File k : kids) deleteTree(k); f.delete(); }

    // ---- DevTools ----------------------------------------------------------------------
    private static String renderSvg(String url) throws Exception {
        // open a tab, get its websocket url
        String tab = putJson(httpBase + "/json/new?about:blank");
        String wsUrl = jsonStr(tab, "webSocketDebuggerUrl"); String targetId = jsonStr(tab, "id");
        Ws ws = new Ws(wsUrl);
        try {
            ws.call("{\"id\":1,\"method\":\"Page.enable\"}");
            ws.call("{\"id\":2,\"method\":\"Runtime.enable\"}");
            ws.call("{\"id\":3,\"method\":\"Page.navigate\",\"params\":{\"url\":" + q(url) + "}}");
            // poll the page: its ?export=svg mode writes <textarea id=spkSvgOut> when ready (or spkSvgErr)
            String expr = "(function(){var t=document.getElementById('spkSvgOut');if(t)return 'OK'+t.value;var e=document.getElementById('spkSvgErr');if(e)return 'ERR'+e.value;return '';})()";
            long deadline = System.currentTimeMillis() + 45000;   // fix9g-b: was 15 s (100k-point pages load + build in ~5 s; keep headroom)
            while (true) {
                String r = ws.call("{\"id\":4,\"method\":\"Runtime.evaluate\",\"params\":{\"expression\":" + q(expr) + ",\"returnByValue\":true}}");
                String v = jsonStr(r, "value");
                if (v != null && v.startsWith("OK")) return v.substring(2);
                if (v != null && v.startsWith("ERR")) throw new IOException("page reported: " + v.substring(3));
                if (System.currentTimeMillis() > deadline) throw new IOException("page did not produce an SVG in 45 s");
                Thread.sleep(40);
            }
        } finally {
            closeTab(ws, targetId);
        }
    }

    /** t2j fix9d: full-page PNG. Opens a tab at 1100 CSS px wide, waits for the page's own
     *  print swap (title SPARKTA_PRINT_READY: canvases replaced by their SVG, so nothing is
     *  mid-animation), reads the laid-out content size and captures beyond the viewport at
     *  the requested device scale. */
    private static byte[] renderPng(String url, double scale) throws Exception {
        String tab = putJson(httpBase + "/json/new?about:blank");
        String wsUrl = jsonStr(tab, "webSocketDebuggerUrl"); String targetId = jsonStr(tab, "id");
        Ws ws = new Ws(wsUrl);
        try {
            ws.call("{\"id\":1,\"method\":\"Page.enable\"}");
            ws.call("{\"id\":2,\"method\":\"Runtime.enable\"}");
            ws.call("{\"id\":3,\"method\":\"Emulation.setDeviceMetricsOverride\",\"params\":{\"width\":1100,\"height\":700,\"deviceScaleFactor\":" + scale + ",\"mobile\":false}}");
            ws.call("{\"id\":4,\"method\":\"Page.navigate\",\"params\":{\"url\":" + q(url) + "}}");
            String expr = "(function(){return document.readyState==='complete'&&document.title==='SPARKTA_PRINT_READY'?'OK':'';})()";
            long deadline = System.currentTimeMillis() + 45000; boolean ready = false;   // fix9g-b: was 15 s
            while (System.currentTimeMillis() < deadline) {
                String r = ws.call("{\"id\":5,\"method\":\"Runtime.evaluate\",\"params\":{\"expression\":" + q(expr) + ",\"returnByValue\":true}}");
                String v = jsonStr(r, "value");
                if (v != null && v.startsWith("OK")) { ready = true; break; }
                Thread.sleep(40);
            }
            if (!ready) say("  saveas: png -- page did not signal print-ready in 45 s, capturing as is");
            Thread.sleep(250);   // let the swapped SVGs lay out
            String lm = ws.call("{\"id\":6,\"method\":\"Page.getLayoutMetrics\"}");
            double h = jsonNumAfter(lm, "cssContentSize", "height"); double w = jsonNumAfter(lm, "cssContentSize", "width");
            if (h <= 0) h = jsonNumAfter(lm, "contentSize", "height");
            if (w <= 0) w = jsonNumAfter(lm, "contentSize", "width");
            if (h <= 0) h = 700; if (w <= 0 || w > 1100) w = 1100;
            String shot = ws.call("{\"id\":7,\"method\":\"Page.captureScreenshot\",\"params\":{\"format\":\"png\",\"captureBeyondViewport\":true,\"clip\":{\"x\":0,\"y\":0,\"width\":" + w + ",\"height\":" + Math.ceil(h) + ",\"scale\":1}}}");
            String b64 = jsonStr(shot, "data");
            if (b64 == null || b64.isEmpty()) throw new IOException("captureScreenshot returned no data: " + shot.substring(0, Math.min(200, shot.length())));
            return java.util.Base64.getDecoder().decode(b64);
        } finally {
            closeTab(ws, targetId);
        }
    }
    /** number value of "key": N inside the object that follows "section": { ... } (first occurrence). */
    private static double jsonNumAfter(String json, String section, String key) {
        int s = json.indexOf("\"" + section + "\"");
        if (s < 0) return -1;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*(-?[0-9.]+)").matcher(json);
        if (!m.find(s)) return -1;
        try { return Double.parseDouble(m.group(1)); } catch (NumberFormatException e) { return -1; }
    }

    /** Minimal request/response over the JDK WebSocket: one in-flight call at a time. */
    private static final class Ws implements WebSocket.Listener {
        private final WebSocket socket; private final StringBuilder buf = new StringBuilder(); private CompletableFuture<String> pending;
        private int expectId = -1;
        Ws(String url) throws Exception { socket = http.newWebSocketBuilder().buildAsync(URI.create(url), this).get(10, TimeUnit.SECONDS); }
        /** fire-and-forget (Page.close: the page may go away before it answers). */
        synchronized void send(String json) throws Exception { socket.sendText(json, true).get(5, TimeUnit.SECONDS); }
        synchronized String call(String json) throws Exception {
            pending = new CompletableFuture<>();
            expectId = Integer.parseInt(json.replaceAll(".*\"id\":(\\d+).*", "$1"));
            socket.sendText(json, true).get(10, TimeUnit.SECONDS);
            return pending.get(60, TimeUnit.SECONDS);   // fix9g-b: was 20 s; a large page takes longer to load and hand back its SVG
        }
        @Override public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
            buf.append(data);
            if (last) { String msg = buf.toString(); buf.setLength(0);
                if (msg.contains("\"id\":" + expectId + ",") || msg.contains("\"id\":" + expectId + "}")) { CompletableFuture<String> p = pending; if (p != null) p.complete(msg); } }
            w.request(1); return null;
        }
        @Override public void onError(WebSocket w, Throwable t) { CompletableFuture<String> p = pending; if (p != null) p.completeExceptionally(t); }
        void close() { try { socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(3, TimeUnit.SECONDS); } catch (Exception ignored) {} }
    }

    // ---- tiny helpers (no JSON library on purpose) -------------------------------------------
    /** fix9h-d: close an export tab for good. The old HTTP GET /json/close/<id> (fired after the
     *  websocket was gone) left tabs behind -- Fahad saw several "SPARKTA_SVG_READY" windows in
     *  Alt+Tab (headless=new lists every target as a window), and the cloud reproduced it (one
     *  lingering tab after three exports). Now: the page is asked to close itself over its own
     *  DevTools socket (Page.close), then the socket is closed, then the HTTP close is tried as
     *  GET and PUT (Chrome moved /json/new to PUT; /json/close may follow), and finally the
     *  target list is checked so a survivor is closed by Target.closeTarget. */
    private static void closeTab(Ws ws, String targetId) {
        try { ws.send("{\"id\":90,\"method\":\"Page.close\"}"); Thread.sleep(80); } catch (Exception ignored) {}
        try { ws.close(); } catch (Exception ignored) {}
        try { getJson(httpBase + "/json/close/" + targetId); } catch (Exception e1) {
            try { putJson(httpBase + "/json/close/" + targetId); } catch (Exception ignored) {} }
        try {
            String list = getJson(httpBase + "/json/list");
            if (list.contains("\"" + targetId + "\"")) {
                String ver = getJson(httpBase + "/json/version");
                String bws = jsonStr(ver, "webSocketDebuggerUrl");
                if (bws != null) { Ws b = new Ws(bws); try { b.call("{\"id\":91,\"method\":\"Target.closeTarget\",\"params\":{\"targetId\":" + q(targetId) + "}}"); } finally { b.close(); } }
            }
        } catch (Exception ignored) {}
    }
    private static String getJson(String url) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400) throw new IOException("HTTP " + r.statusCode() + " for " + url); return r.body();
    }
    private static String putJson(String url) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(5)).PUT(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400) throw new IOException("HTTP " + r.statusCode() + " for " + url); return r.body();
    }
    /** value of a top-level or nested string key: "key":"..." with JSON escapes decoded. */
    private static String jsonStr(String json, String key) {
        // t1e: Chrome pretty-prints ("key": "value", with whitespace after the colon) -- accept both
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"").matcher(json);
        if (!m.find()) return null;
        int s = m.end(); StringBuilder sb = new StringBuilder();
        for (int p = s; p < json.length(); p++) { char c = json.charAt(p);
            if (c == '\\') { char n = json.charAt(++p);
                if (n == 'n') sb.append('\n'); else if (n == 't') sb.append('\t'); else if (n == 'r') sb.append('\r');
                else if (n == 'u') { sb.append((char) Integer.parseInt(json.substring(p + 1, p + 5), 16)); p += 4; } else sb.append(n); }
            else if (c == '"') break; else sb.append(c); }
        return sb.toString();
    }
    private static String q(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""; }

    public static void main(String[] a) { System.exit(execute(a)); }
}
