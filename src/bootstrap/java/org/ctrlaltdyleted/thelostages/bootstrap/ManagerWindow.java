package org.ctrlaltdyleted.thelostages.bootstrap;

import net.minecraftforge.forgespi.locating.IModFile;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;

final class ManagerWindow {
    private static final String REQUIRED = "Install All Required Mods";
    private static final String EVERYTHING = "Install All Required Mods and Optional Resource Packs";
    private static final String INSTRUCTIONS = "Open the required download pages on CurseForge and download the files. The Lost Ages will detect and verify them before installation.";
    private static final String OPTIONAL = "These resource packs were included in earlier versions of Create - Forge Frontier. Install either or both if you want.";
    private static final String RESTART = "After installation finishes, click Close Minecraft, then launch the game again to load the newly installed mods/resource packs.";
    private static final String SUCCESS = "Required mods installed successfully.";
    private static final String SUCCESS_RESTART = "Click Close Minecraft, then launch the game again to load the newly installed mods/resource packs.";
    private static final String REQUIRED_TIP = "The version of this mod required by The Lost Ages 1.0.0.";
    private static final String DETECTED_TIP = "The version currently installed in this Minecraft instance.";
    private static final String CONFLICT_TIP = "An incompatible version is installed. It must be replaced with the required version.";
    private static final String PACK_TIP = "OpenLoader will automatically enable installed resource packs when Minecraft restarts.";
    private static final String VERIFIED_TIP = "Downloaded files are checked before installation.";

    private static Font regular = new Font(Font.DIALOG, Font.PLAIN, 13);
    private static Font semibold = new Font(Font.DIALOG, Font.BOLD, 13);
    private JFrame frame;
    private JScrollPane scroll;
    private int previousTooltipDelay = -1;
    private final JPanel content = new JPanel(new BorderLayout(0, 10)) {
        @Override public void paint(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                super.paint(g);
            } finally { g.dispose(); }
        }
    };
    private final JLabel summary = new JLabel(" ");
    private final JTextArea restart = text(RESTART, 2);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "Lost Ages dependency installer"));
    private final CountDownLatch closed = new CountDownLatch(1);
    private final List<Row> entries = new ArrayList<>();
    private final List<JButton> bulkButtons = new ArrayList<>();
    private final Path game, mods;
    private final List<IModFile> discovered;
    private final Theme theme;
    private final BufferedImage logo;
    private boolean busy, disposed, openingPages;
    private Installation.Downloads downloads;
    private Path watchedFolder;
    private final JTextArea watched = text("Finding download folder\u2026", 2);
    private final JLabel counts = new JLabel(" ");
    private JButton requiredInstall, allInstall, retry, openRequired, openEverything;

    static void showAndWait(Path game, Path mods, List<LostAgesDependencyLocator.Problem> problems,
                            List<Dependency> catalog, List<IModFile> discovered) {
        loadFonts();
        Boolean dark = windowsDarkMode(); // Registry/file work stays off Swing's event thread.
        BufferedImage logo = null;
        try { logo = loadLogo(); } catch (IOException e) { log(System.Logger.Level.WARNING, "Could not load The Lost Ages logo", e); }
        BufferedImage image = logo;
        try {
            var holder = new ManagerWindow[1];
            SwingUtilities.invokeAndWait(() -> {
                holder[0] = new ManagerWindow(game, mods, problems, catalog, discovered, appearance(dark), image);
                holder[0].open();
            });
            holder[0].closed.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Dependency installer interrupted. See .thelostages/missing-dependencies.txt", e);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new IllegalStateException("Cannot open dependency installer. See .thelostages/missing-dependencies.txt", e.getCause());
        }
    }

    private ManagerWindow(Path game, Path mods, List<LostAgesDependencyLocator.Problem> problems,
                          List<Dependency> catalog, List<IModFile> discovered, Theme theme, BufferedImage logo) {
        this.game = game; this.mods = mods; this.discovered = discovered; this.theme = theme; this.logo = logo;
        for (Object key : java.util.Collections.list(UIManager.getDefaults().keys())) {
            if (key instanceof String name && name.endsWith(".font")) UIManager.put(key, font(false, 13, ""));
        }
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        content.setBackground(theme.background());
        JPanel header = vertical();
        JPanel branding = new JPanel(new BorderLayout(12, 0)); branding.setOpaque(false); branding.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (logo != null) {
            JLabel image = new JLabel(new ImageIcon(logo.getScaledInstance(96, 96, Image.SCALE_SMOOTH)));
            image.getAccessibleContext().setAccessibleName("The Lost Ages logo"); branding.add(image, BorderLayout.WEST);
        }
        JPanel title = vertical();
        title.add(label("The Lost Ages", 24, true, theme.text()));
        title.add(label("Dependency Installer", 14, false, theme.muted()));
        title.add(Box.createVerticalStrut(10));
        title.add(label("The Lost Ages requires the following mods.", 15, true, theme.text()));
        branding.add(title, BorderLayout.CENTER); header.add(branding);
        header.add(Box.createVerticalStrut(8));
        JTextArea instructions = text(INSTRUCTIONS, 2); instructions.setForeground(theme.text()); header.add(instructions);
        JPanel location = new JPanel(new BorderLayout(8, 0)); location.setOpaque(false); location.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton choose = button("Choose Download Folder", this::chooseDownloadFolder); mnemonic(choose, KeyEvent.VK_L);
        location.add(choose, BorderLayout.WEST); watched.setForeground(theme.muted()); watched.setWrapStyleWord(false); location.add(watched, BorderLayout.CENTER);
        header.add(Box.createVerticalStrut(6)); header.add(location);
        content.add(header, BorderLayout.NORTH);

        DependencyList rows = new DependencyList(); rows.setBackground(theme.background());
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        // Keep the installation model in catalog order; only the visual sections move.
        for (var dep : catalog) if (!dep.pack()) entries.add(new Row(dep,
                problems.stream().filter(p -> p.dependency().id().equals(dep.id())).findFirst().orElse(null)));
        List<Row> requiredRows = List.copyOf(entries);
        JPanel optionalTitle = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); optionalTitle.setOpaque(false); optionalTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        optionalTitle.add(section("Optional Resource Packs"));
        JLabel info = label("  \u24d8", 14, false, theme.muted()); info.setToolTipText(PACK_TIP);
        info.getAccessibleContext().setAccessibleName("Optional resource packs information"); optionalTitle.add(info);
        optionalTitle.setMaximumSize(new Dimension(Integer.MAX_VALUE, optionalTitle.getPreferredSize().height)); rows.add(optionalTitle);
        JTextArea optional = text(OPTIONAL, 2); optional.setForeground(theme.muted()); rows.add(optional);
        for (var dep : catalog) if (dep.pack()) { Row row = new Row(dep, null); entries.add(row); rows.add(row); }
        rows.add(section("Required Mods"));
        requiredRows.forEach(rows::add);
        rows.add(Box.createVerticalGlue());
        scroll = new JScrollPane(rows, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(theme.border())); scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
                g.setColor(theme.background()); g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
            @Override protected void paintThumb(Graphics g, JComponent c, Rectangle bounds) {
                g.setColor(isThumbRollover() ? theme.muted() : theme.border());
                g.fillRoundRect(bounds.x + 3, bounds.y + 2, Math.max(4, bounds.width - 6), Math.max(4, bounds.height - 4), 6, 6);
            }
        });
        scroll.getViewport().setBackground(theme.background()); content.add(scroll, BorderLayout.CENTER);

        JPanel footer = vertical();
        counts.setForeground(theme.muted()); footer.add(counts);
        JPanel primary = new JPanel(new GridLayout(4, 1, 0, 3)); primary.setOpaque(false); primary.setAlignmentX(Component.LEFT_ALIGNMENT);
        openRequired = addBulk(primary, "Open Required Mods on CurseForge", KeyEvent.VK_D, () -> openPages(false));
        openEverything = addBulk(primary, "Open Required Mods and Optional Resource Packs on CurseForge", KeyEvent.VK_P, () -> openPages(true));
        requiredInstall = addBulk(primary, REQUIRED, KeyEvent.VK_R, () -> run(selectRows(false)));
        allInstall = addBulk(primary, EVERYTHING, KeyEvent.VK_A, () -> run(selectRows(true)));
        footer.add(primary); footer.add(Box.createVerticalStrut(7));
        summary.setFont(font(true, 13, SUCCESS)); summary.setForeground(theme.success()); footer.add(summary);
        restart.setForeground(theme.text()); footer.add(restart); footer.add(Box.createVerticalStrut(7));
        JPanel controls = new JPanel(new BorderLayout(8, 0)); controls.setOpaque(false); controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel secondary = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); secondary.setOpaque(false);
        retry = addBulk(secondary, "Retry Failed", KeyEvent.VK_F, () -> run(entries.stream().filter(r -> r.failed && r.dep.pack()).toList()));
        secondary.add(Box.createHorizontalStrut(6));
        JButton folder = button("Open Mods Folder", () -> desktop(() -> { Files.createDirectories(mods); Desktop.getDesktop().open(mods.toFile()); }));
        mnemonic(folder, KeyEvent.VK_O); secondary.add(folder); controls.add(secondary, BorderLayout.WEST);
        JButton close = button("Close Minecraft", this::close); mnemonic(close, KeyEvent.VK_C);
        close.setFont(font(true, 13, close.getText())); controls.add(close, BorderLayout.EAST);
        footer.add(controls); content.add(footer, BorderLayout.SOUTH);
        updateCompletion();
    }

    private void open() {
        frame = new JFrame("The Lost Ages: Dependency Installer");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { close(); } });
        if (logo != null) frame.setIconImage(logo);
        frame.setContentPane(content);
        Rectangle screen = frame.getGraphicsConfiguration().getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(frame.getGraphicsConfiguration());
        int width = Math.min(780, screen.width - insets.left - insets.right - 24);
        int height = Math.min(680, screen.height - insets.top - insets.bottom - 24);
        frame.setSize(width, height);
        frame.setMinimumSize(new Dimension(Math.min(640, width), Math.min(520, height)));
        tooltipTiming(true);
        frame.setLocationRelativeTo(null); frame.setVisible(true);
        startMonitoring();
        // One post-layout initialization; progress updates never alter the viewport.
        SwingUtilities.invokeLater(() -> {
            if (frame.isDisplayable()) {
                bulkButtons.get(0).requestFocusInWindow();
                scroll.getViewport().setViewPosition(new Point(0, 0));
            }
        });
    }

    private List<Row> selectRows(boolean includePacks) {
        return entries.stream().filter(r -> !r.done && (includePacks || !r.dep.pack())).toList();
    }

    private JButton addBulk(JPanel panel, String text, int mnemonic, Runnable action) {
        JButton button = button(text, action); mnemonic(button, mnemonic); panel.add(button); bulkButtons.add(button); return button;
    }

    private final class Row extends JPanel {
        final Dependency dep;
        final LostAgesDependencyLocator.Problem problem;
        final JTextArea status = text("", 1);
        JTextArea detected;
        final JProgressBar progress = new JProgressBar();
        final JButton install;
        boolean done, failed, installing;
        Path source;
        Row(Dependency dep, LostAgesDependencyLocator.Problem problem) {
            super(new BorderLayout(10, 0)); this.dep = dep; this.problem = problem;
            setBackground(theme.panel()); setAlignmentX(Component.LEFT_ALIGNMENT);
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.border()), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            JPanel details = vertical();
            JLabel name = label(dep.name(), 14, true, theme.text()); details.add(name);
            if (!dep.pack()) {
                JLabel required = label("Required: " + dep.range(), 12, false, theme.muted()); required.setToolTipText(REQUIRED_TIP); details.add(required);
                detected = text("Detected: " + (problem == null ? discovered.stream()
                        .map(IModFile::getModFileInfo).filter(java.util.Objects::nonNull)
                        .flatMap(info -> info.getMods().stream()).filter(mod -> dep.id().equals(mod.getModId()))
                        .map(mod -> mod.getVersion().toString()).findFirst().orElse("Missing") : problem.detected()), 1); detected.setForeground(theme.muted()); detected.setToolTipText(DETECTED_TIP); details.add(detected);
                if (problem != null && !problem.installed().isEmpty()) { name.setToolTipText(CONFLICT_TIP); status.setToolTipText(CONFLICT_TIP); }
            }
            status.setForeground(problem != null && !problem.installed().isEmpty() ? theme.warning() : theme.muted());
            done = !dep.pack() && problem == null;
            status.setText(done ? "Already installed" : "Not downloaded"); details.add(status);
            progress.setUI(new javax.swing.plaf.basic.BasicProgressBarUI()); progress.setForeground(theme.accent()); progress.setBackground(theme.border()); progress.setBorderPainted(false);
            progress.setPreferredSize(new Dimension(0, 6)); progress.setVisible(false); progress.setToolTipText(VERIFIED_TIP); details.add(progress);
            add(details, BorderLayout.CENTER);
            JPanel actions = new JPanel(new GridLayout(dep.pack() ? 2 : 1, 1, 0, 4)); actions.setOpaque(false);
            install = dep.pack() ? button("Install", () -> run(List.of(this))) : null;
            if (install != null) { install.setEnabled(false); actions.add(install); }
            actions.add(button("Official Download Page", () -> requestPages(List.of(dep))));
            add(actions, BorderLayout.EAST);
            if (dep.pack()) worker.submit(() -> {
                try {
                    if (Installation.installedPack(game.resolve("resourcepacks"), dep).isPresent()) SwingUtilities.invokeLater(() -> {
                        done = true; status.setText("Already installed"); status.setForeground(theme.success()); updateCompletion();
                    });
                } catch (IOException e) { log(System.Logger.Level.DEBUG, "Could not inspect installed resource packs", e); }
            });
        }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        void detected(Installation.Found found) {
            source = found == null ? null : found.file();
            if (!done && !installing && !failed) {
                status.setText(found == null ? "Not downloaded" : found.status());
                status.setForeground(found != null && "Invalid file".equals(found.status()) ? theme.error() : source != null ? theme.success() : theme.muted());
            }
        }
        void copying() {
            installing = true; failed = false; status.setRows(1); status.setText("Installing\u2026"); status.setForeground(theme.accent()); status.setToolTipText(VERIFIED_TIP);
            progress.setVisible(true); progress.setIndeterminate(true); revalidate();
        }
        void installed(String version) {
            if (detected != null && version != null) detected.setText("Detected: " + version);
            done = true; installing = false; failed = false; progress.setIndeterminate(false); progress.setValue(100);
            status.setText("Installed"); status.setForeground(theme.success()); status.setToolTipText(VERIFIED_TIP);
            updateCompletion(); revalidate();
        }
        void failed(Exception error) {
            log(System.Logger.Level.WARNING, "Could not install " + dep.name(), error);
            failed = true; installing = false; progress.setIndeterminate(false); progress.setVisible(false);
            status.setRows(Math.max(3, Math.min(8, (String.valueOf(error.getMessage()).length() + 54) / 55))); status.setText("Installation failed: " + error.getMessage()); status.setForeground(theme.error()); status.setToolTipText(null);
            status.getAccessibleContext().setAccessibleDescription(error.toString()); revalidate();
        }
    }

    private record InstallJob(Row row, Path source, List<Path> conflicts) {}
    private void run(List<Row> selected) {
        if (busy || selected.isEmpty()) return;
        if (selected.stream().anyMatch(r -> !r.done && r.source == null)) { summary.setText("Download and verify every selected file first."); return; }
        List<InstallJob> jobs = new ArrayList<>();
        for (Row row : selected) {
            if (row.done) continue;
            var conflicts = row.problem == null ? List.<Path>of() : row.problem.installed().stream().map(LostAgesDependencyLocator.Installed::file).distinct().toList();
            if (!conflicts.isEmpty() && !confirmReplacement(row, conflicts)) return;
            jobs.add(new InstallJob(row, row.source, conflicts));
        }
        if (jobs.isEmpty()) { updateCompletion(); return; }
        setBusy(true);
        worker.submit(() -> installJobs(List.copyOf(jobs)));
    }
    private boolean confirmReplacement(Row row, List<Path> conflicts) {
        boolean shared = discovered.stream().filter(f -> conflicts.contains(f.getFilePath())).anyMatch(f -> f.getModFileInfo().getMods().size() != 1);
        if (shared) { row.status.setText("This JAR contains multiple mods. Resolve the conflict manually and restart."); return false; }
        return JOptionPane.showConfirmDialog(frame, "Replace conflicting " + row.dep.name() + " installation?\n" + conflicts
                + "\nOriginals will be moved to " + game.resolve(".thelostages/backups")
                + "\nTo recover: close Minecraft, remove the new JAR and restore the backup to mods with its original name.\n"
                + "If the launcher locks a file, close Minecraft and resolve it manually.", "Confirm replacement", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }
    private void installJobs(List<InstallJob> jobs) {
        try {
            for (var job : jobs) {
                Row row = job.row(); SwingUtilities.invokeLater(row::copying);
                try {
                    Path destination = Installation.install(row.dep, job.source(), row.dep.pack() ? game.resolve("resourcepacks") : mods, job.conflicts(), game.resolve(".thelostages/backups"), true,
                            bytes -> SwingUtilities.invokeLater(() -> row.status.setText("Copied " + bytes / 1024 + " KiB\u2026")));
                    String version = row.dep.pack() ? null : Installation.validate(destination, row.dep);
                    SwingUtilities.invokeLater(() -> row.installed(version));
                } catch (IOException | RuntimeException e) { SwingUtilities.invokeLater(() -> row.failed(e)); }
            }
        } finally { SwingUtilities.invokeLater(() -> { setBusy(false); updateCompletion(); }); }
    }
    private void updateCompletion() {
        long required = entries.stream().filter(r -> !r.dep.pack() && (r.done || r.source != null)).count();
        long packs = entries.stream().filter(r -> r.dep.pack() && (r.done || r.source != null)).count();
        counts.setText("Required mods verified: " + required + "/9    Optional resource packs verified: " + packs + "/2");
        if (entries.stream().filter(r -> !r.dep.pack()).allMatch(r -> r.done)) {
            summary.setText(SUCCESS); summary.setIcon(new StateIcon(theme.success())); summary.setForeground(theme.success()); restart.setText(SUCCESS_RESTART);
        } else {
            summary.setText(busy ? "Installing verified files\u2026" : "Download and verify the remaining files before installation.");
        }
        updateControls();
    }
    private void updateControls() {
        if (requiredInstall == null) return;
        var required = selectRows(false); var all = selectRows(true);
        requiredInstall.setEnabled(!busy && !required.isEmpty() && required.stream().allMatch(r -> r.source != null));
        allInstall.setEnabled(!busy && !all.isEmpty() && all.stream().allMatch(r -> r.source != null));
        retry.setEnabled(!busy && entries.stream().anyMatch(r -> r.dep.pack() && r.failed && !r.done && r.source != null));
        openRequired.setEnabled(!openingPages && !pagesToOpen(false).isEmpty());
        openEverything.setEnabled(!openingPages && !pagesToOpen(true).isEmpty());
        entries.forEach(r -> { if (r.install != null) r.install.setEnabled(!busy && !r.done && r.source != null); });
    }
    private void setBusy(boolean busy) { this.busy = busy; updateControls(); }
    private List<Dependency> pagesToOpen(boolean includePacks) {
        return entries.stream().filter(r -> !r.done && r.source == null && (includePacks || !r.dep.pack())).map(r -> r.dep).toList();
    }
    private void openPages(boolean includePacks) { requestPages(pagesToOpen(includePacks)); }
    private void requestPages(List<Dependency> pages) {
        if (openingPages || pages.isEmpty()) return;
        if (pages.size() > 1 && JOptionPane.showConfirmDialog(frame, "Open " + pages.size() + " CurseForge file pages in your default browser?",
                "Open CurseForge pages", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        openingPages = true; updateControls();
        worker.submit(() -> {
            List<String> failures = new ArrayList<>();
            for (Dependency dep : pages) {
                if (Thread.currentThread().isInterrupted()) break;
                try {
                    var uri = Installation.officialPage(dep);
                    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) throw new IOException("Default browser unavailable");
                    Desktop.getDesktop().browse(uri);
                } catch (IOException | RuntimeException e) { failures.add(dep.name()); log(System.Logger.Level.WARNING, "Could not open official page for " + dep.name(), e); }
            }
            SwingUtilities.invokeLater(() -> {
                if (disposed) return;
                openingPages = false; updateControls();
                if (!failures.isEmpty()) JOptionPane.showMessageDialog(frame, "Could not open these CurseForge pages: " + String.join(", ", failures));
            });
        });
    }
    private void startMonitoring() {
        if (downloads != null || disposed) return;
        downloads = new Installation.Downloads(game, entries.stream().map(r -> r.dep).toList(), snapshot -> SwingUtilities.invokeLater(() -> {
            if (disposed) return;
            watchedFolder = snapshot.folder();
            watched.setText(watchedFolder == null ? snapshot.message() : "Download folder: " + watchedFolder
                    + ("Watching this folder".equals(snapshot.message()) ? "" : "\n" + snapshot.message()));
            watched.setToolTipText(watchedFolder == null ? null : watchedFolder.toString());
            entries.forEach(row -> row.detected(snapshot.files().get(row.dep.id()))); updateCompletion();
        }));
    }
    private void chooseDownloadFolder() {
        JFileChooser chooser = new JFileChooser(); chooser.setDialogTitle("Choose Download Folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); chooser.setAcceptAllFileFilterUsed(false);
        if (watchedFolder != null) chooser.setCurrentDirectory(watchedFolder.toFile());
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            startMonitoring(); downloads.choose(chooser.getSelectedFile().toPath());
        }
    }
    private void close() {
        if (busy) { JOptionPane.showMessageDialog(frame, "Wait for the current installation to finish before exiting."); return; }
        disposed = true; if (downloads != null) downloads.close();
        worker.shutdownNow(); tooltipTiming(false); if (frame != null) frame.dispose(); closed.countDown();
    }
    @FunctionalInterface private interface DesktopAction { void run() throws Exception; }
    private void desktop(DesktopAction action) {
        worker.submit(() -> { try { action.run(); } catch (Exception e) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Could not open desktop action: " + e.getMessage() + "\nMods: " + mods));
        } });
    }

    private JLabel section(String text) {
        JLabel label = label(text, 14, true, theme.text()); label.setBorder(BorderFactory.createEmptyBorder(8, 4, 6, 4)); return label;
    }
    private static JPanel vertical() { JPanel panel = new JPanel(); panel.setOpaque(false); panel.setAlignmentX(Component.LEFT_ALIGNMENT); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); return panel; }
    private static JLabel label(String text, int size, boolean bold, Color color) {
        JLabel label = new JLabel(text); label.setFont(font(bold, size, text)); label.setForeground(color); label.setAlignmentX(Component.LEFT_ALIGNMENT); return label;
    }
    private static JTextArea text(String text, int lines) {
        JTextArea area = new JTextArea() {
            @Override public void setText(String value) {
                super.setText(value); setFont(font(false, 13, value));
            }
        };
        area.setRows(lines); area.setLineWrap(true); area.setWrapStyleWord(true); area.setEditable(false); area.setFocusable(false);
        area.setUI(new javax.swing.plaf.basic.BasicTextAreaUI()); area.setBorder(BorderFactory.createEmptyBorder());
        // Read-only status documents must not scroll their ancestors when changed on the EDT.
        ((DefaultCaret) area.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        area.setText(text); area.setOpaque(false); area.setAlignmentX(Component.LEFT_ALIGNMENT); return area;
    }
    private static void mnemonic(JButton button, int key) {
        button.setMnemonic(key);
        button.setDisplayedMnemonicIndex(-1); // Supported display-only change; Alt bindings remain installed.
    }
    private void tooltipTiming(boolean active) {
        ToolTipManager manager = ToolTipManager.sharedInstance();
        if (active && previousTooltipDelay < 0) {
            previousTooltipDelay = manager.getInitialDelay(); manager.setInitialDelay(200);
        } else if (!active && previousTooltipDelay >= 0) {
            manager.setInitialDelay(previousTooltipDelay); previousTooltipDelay = -1;
        }
    }
    static void loadFonts() {
        regular = loadFont("Exo-Regular.ttf", Font.PLAIN);
        semibold = loadFont("Exo-SemiBold.ttf", Font.BOLD);
    }
    private static Font loadFont(String resource, int fallbackStyle) {
        try (var in = ManagerWindow.class.getResourceAsStream("/fonts/exo/" + resource)) {
            if (in == null) throw new IOException("Missing packaged font " + resource);
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException | FontFormatException e) {
            log(System.Logger.Level.WARNING, "Could not load Exo; using the system font", e);
            return new Font(Font.DIALOG, fallbackStyle, 13);
        }
    }
    private static Font font(boolean heading, float size, String text) {
        Font face = (heading ? semibold : regular).deriveFont(size);
        // Logical fonts retain Java's platform composite fallback for unsupported Unicode.
        return text == null || face.canDisplayUpTo(text) < 0 ? face
                : new Font(Font.DIALOG, heading ? Font.BOLD : Font.PLAIN, 1).deriveFont(size);
    }

    private JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override protected void paintText(Graphics g, JComponent c, Rectangle bounds, String value) {
                if (c.isEnabled()) { super.paintText(g, c, bounds, value); return; }
                g.setColor(theme.muted());
                javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt(g, value, -1,
                        bounds.x, bounds.y + g.getFontMetrics().getAscent());
            }
            @Override protected void paintFocus(Graphics g, AbstractButton b, Rectangle view, Rectangle text, Rectangle icon) {
                g.setColor(theme.accent()); g.drawRect(3, 3, b.getWidth() - 7, b.getHeight() - 7);
            }
        });
        button.setBackground(theme.panel()); button.setForeground(theme.text());
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(theme.border()), BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        button.setFont(font(false, 13, text)); button.setOpaque(true); button.setRolloverEnabled(true);
        button.addChangeListener(e -> button.setBackground(button.getModel().isRollover() || button.getModel().isPressed()
                ? new Color((theme.panel().getRed() * 9 + theme.accent().getRed()) / 10,
                        (theme.panel().getGreen() * 9 + theme.accent().getGreen()) / 10,
                        (theme.panel().getBlue() * 9 + theme.accent().getBlue()) / 10) : theme.panel()));
        button.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "pressed");
        button.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released");
        button.addActionListener(e -> action.run()); return button;
    }
    static BufferedImage loadLogo() throws IOException {
        try (var in = ManagerWindow.class.getResourceAsStream("/thelostages.png")) {
            if (in == null) throw new IOException("Missing packaged thelostages.png");
            BufferedImage logo = ImageIO.read(in); if (logo == null) throw new IOException("Invalid logo image"); return logo;
        }
    }
    private static void log(System.Logger.Level level, String text, Throwable error) { System.getLogger(ManagerWindow.class.getName()).log(level, text, error); }
    private static final class DependencyList extends JPanel implements Scrollable {
        public Dimension getPreferredScrollableViewportSize() { return new Dimension(700, 280); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(24, r.height - 24); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
    private record StateIcon(Color color) implements Icon {
        public int getIconWidth() { return 14; } public int getIconHeight() { return 14; }
        public void paintIcon(Component c, Graphics g, int x, int y) { g.setColor(color); g.fillOval(x + 2, y + 2, 10, 10); }
    }

    private record Theme(Color background, Color panel, Color text, Color muted, Color border, Color accent, Color success, Color warning, Color error) {
        static Theme mode(boolean dark) {
            return dark ? new Theme(new Color(0x181D24), new Color(0x242B35), new Color(0xF2F4F8), new Color(0xB9C3D1), new Color(0x465363), new Color(0x98CBFF), new Color(0x8BD9A0), new Color(0xFFD18A), new Color(0xFFAAAD))
                    : new Theme(new Color(0xF4F6F9), Color.WHITE, new Color(0x17212F), new Color(0x48586B), new Color(0xCAD2DD), new Color(0x174C82), new Color(0x216739), new Color(0x804E00), new Color(0xA52432));
        }
    }
    private static Theme appearance(Boolean dark) {
        try {
            boolean highContrast = Boolean.TRUE.equals(Toolkit.getDefaultToolkit().getDesktopProperty("win.highContrast.on"));
            if (dark != null && !highContrast) {
                Theme t = Theme.mode(dark);
                UIManager.put("control", t.panel()); UIManager.put("nimbusBase", t.border()); UIManager.put("text", t.text());
                UIManager.put("nimbusBlueGrey", t.border()); UIManager.put("info", t.panel()); UIManager.put("infoText", t.text());
                UIManager.put("nimbusLightBackground", t.background()); UIManager.put("nimbusSelectionBackground", t.accent());
                UIManager.put("nimbusSelectedText", dark ? Color.BLACK : Color.WHITE); UIManager.put("nimbusFocus", t.accent());
                UIManager.put("nimbusDisabledText", t.muted()); UIManager.setLookAndFeel(new NimbusLookAndFeel());
                UIManager.put("Button.disabledText", t.muted());
                UIManager.put("ToolTip.background", t.panel()); UIManager.put("ToolTip.foreground", t.text());
                UIManager.put("ProgressBar.foreground", t.accent()); return t;
            }
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) { log(System.Logger.Level.DEBUG, "Using the default Swing look and feel", e); }
        Color background = UIManager.getColor("Panel.background");
        return Theme.mode(background != null && background.getRed() + background.getGreen() + background.getBlue() < 384);
    }
    static Boolean windowsDarkMode() {
        if (!System.getProperty("os.name", "").startsWith("Windows")) return null;
        Process process = null;
        try {
            String executable = Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"), "System32", "reg.exe").toString();
            process = new ProcessBuilder(executable, "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme").redirectErrorStream(true).start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) { process.destroyForcibly(); return null; }
            String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() == 0) {
                var match = java.util.regex.Pattern.compile("AppsUseLightTheme\\s+REG_DWORD\\s+0x([01])\\b").matcher(result);
                if (match.find()) return match.group(1).equals("0");
            }
        } catch (IOException | SecurityException e) { log(System.Logger.Level.DEBUG, "Could not read Windows app appearance", e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        finally { if (process != null) { try { process.getInputStream().close(); } catch (IOException ignored) {} process.destroy(); } }
        return null;
    }
}
