package org.simonhareter.springinit;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.simonhareter.springinit.libc.Terminal;
import org.simonhareter.springinit.libc.WindowSize;
import org.simonhareter.springinit.util.CursorPosition;
import org.simonhareter.springinit.util.Dependencies;
import org.simonhareter.springinit.util.Dependency;
import org.simonhareter.springinit.util.DependencyGroup;
import org.simonhareter.springinit.util.DependencyRow;
import org.simonhareter.springinit.util.Dialog;
import org.simonhareter.springinit.util.DialogRow;
import org.simonhareter.springinit.util.Direction;
import org.simonhareter.springinit.util.Header;
import org.simonhareter.springinit.util.MetaData;
import org.simonhareter.springinit.util.MetaDataCache;
import org.simonhareter.springinit.util.MetaDataConfig;
import org.simonhareter.springinit.util.MetaDataOption;
import org.simonhareter.springinit.util.Project;
import org.simonhareter.springinit.util.SectionLayout;
import org.simonhareter.springinit.util.Spacer;
import org.simonhareter.springinit.util.TextField;
import org.simonhareter.springinit.util.TextSegment;
import org.simonhareter.springinit.util.VisibleRange;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class SpringInit {

    // ----------------- Main TUI State ----------------------------------

    private final Terminal terminal;
    private final ObjectMapper mapper;
    private final List<List<Integer>> menuGrid;
    private final int[] previousSelection;
    private final int[] currentSelection;
    private final String version = "0.0.1";

    private static final String[] LOGO = {
            "  ____             _                  ___       _ _   _       _ _          ",
            " / ___| _ __  _ __(_)_ __   __ _     |_ _|_ __ (_) |_(_) __ _| (_)_____ __ ",
            " \\___ \\| '_ \\| '__| | '_ \\ / _` |_____| || '_ \\| | __| |/ _` | | |_  / '__|",
            "  ___) | |_) | |  | | | | | (_| |_____| || | | | | |_| | (_| | | |/ /| |   ",
            " |____/| .__/|_|  |_|_| |_|\\__, |    |___|_| |_|_|\\__|_|\\__,_|_|_/___|_|   ",
            "       |_|                 |___/                                            "
    };

    private MetaData data;
    private MetaDataCache cache;
    private MetaDataConfig config;
    private Dependencies dependencies;

    private final Path home = Path.of(System.getProperty("user.home"));
    private final Path configDir = this.home.resolve(".config").resolve("spring-initializr-tui");
    private final Path cacheDir = this.home.resolve(".cache").resolve("spring-initializr-tui");
    private final Path configFile = this.configDir.resolve("config.json");
    private final Path cacheFile = this.cacheDir.resolve("cache.json");

    private WindowSize windowSize;
    private int rows, columns;

    private boolean isRunning, isEditing, isPostGenMenuRunning, isAddDependencyRunning, updatePackageName, isDimmed,
            firstRender, dependencyFirstRender;
    private int postGenMenuIndex = 0;

    // Cursor position inside the menu grid
    private int cursorX = 0, cursorY = 0, previousCursorY;

    // Virtual cursor position
    private int scrollOffset = 0, oldScrollOffset = 0, viewPortHeight,
            statusBarHeight = 1,
            debugHeight = 1;
    private static final int SCROLL_MARGIN = 3;

    private SectionLayout logoL, project, language, bootVersion, projectMetaData, groupL, artifactL, packageNameL,
            packaging, configuration, javaVersion, addDep, generate, postGen;
    private SectionLayout[] sections;

    private CursorPosition textCursorPos;
    private TextField group, artifact, packageName;
    private static final int TEXT_START = 26;

    // ----------------- Colors / UI constants --------------------------

    private static final String SELECTED = "\u25CF"; // ●
    private static final String UNSELECTED = "\u25CB"; // ○
    private static final String UNDERLINED = "\033[4m";
    private static final String RESET_UNDERLINED = "\033[24m";
    private static final String GREEN = "\033[38;2;109;179;63m";
    private static final String WHITE = "\033[38;2;193;193;193m";
    private static final String RED = "\033[38;2;220;50;47m";
    private static final String BG = "\033[48;2;21;21;31m";
    private static final String BG_DIMMED = "\033[48;2;10;10;20m";
    private static final String DIMMED = "\033[2m";
    private static final String RESET_DIMMED = "\033[22m";
    private static final String BUTTON_BG_SELECTED = "\033[48;2;50;80;30m";
    private static final String BUTTON_BG_UNSELECTED = "\033[48;2;33;33;48m";
    private static final String RESET_BUTTON_BG = "\033[48;2;21;21;31m";
    private static final String RESET_COLOR = "\033[0m";
    private static final String BORDER_COLOR = "\033[38;5;240m";
    private static final String DEP_LINE_COLOR = "\033[48;2;33;33;42m";

    // ----------------- Dependency Dialog Window State -----------------

    public static final char TL = '╭';
    public static final char TR = '╮';
    public static final char BL = '╰';
    public static final char BR = '╯';
    public static final char H = '─';
    public static final char V = '│';

    private List<DialogRow> DEPENDENCY_MENU_ITEMS;
    private Spacer spacer;
    private Header filter, selected, available, nothingSelected;
    private Dialog dependencyDialog;
    private int depCursorY = 0, previousDepCursorY = 0, depScrollOffsetY = 0;
    private DependencyRow[] allDependencies;
    private List<DependencyRow> availableDependencies, tempSelectedDependencies, selectedDependencies;
    private DependencyGroup selectedGroup;
    private static final int SCROLL_DEP_MARGIN = 5;
    private boolean scrollOffsetChanged, tempSelectedDepListChanged;

    // ------------------------------------------------------------------

    public SpringInit(Terminal terminal) {
        this.terminal = terminal;
        this.mapper = new ObjectMapper();
        this.menuGrid = new ArrayList<>();
        this.previousSelection = new int[11];
        this.currentSelection = new int[11];
        this.selectedDependencies = new ArrayList<>();
        this.tempSelectedDependencies = new ArrayList<>();
        this.DEPENDENCY_MENU_ITEMS = new ArrayList<>();
    }

    public void start() {
        enterAlternateBuffer();
        renderLoading();
        init();
        calculateContentHeight();
        hideCursor();
        renderUI();
        renderStatusBar();

        while (this.isRunning) {
            int key = readKey();
            boolean shouldRender = handleKey(key);
            if (shouldRender) {
                renderUI();
                renderStatusBar();
            }
        }

        leaveAlternateBuffer();
        System.exit(0);
    }

    private void enterAlternateBuffer() {
        IO.print("\033[?47h");
        IO.print("\033[?1049h");
    }

    private void leaveAlternateBuffer() {
        IO.print("\033[?47l");
        IO.print("\033[?1049l");
    }

    private void init() {
        this.isRunning = true;
        this.firstRender = true;

        terminal.enableRawMode();

        this.windowSize = this.terminal.getWindowSize();
        this.rows = this.windowSize.rows();
        this.columns = this.windowSize.columns();

        this.viewPortHeight = this.rows - this.statusBarHeight - this.debugHeight;

        int width = (int) (this.columns * 0.8);
        int height = (int) (this.rows * 0.8);

        // ANSI is 1-based, so + 1
        int x = (this.columns - width) / 2 + 1;
        int y = (this.rows - height) / 2 + 1;

        this.dependencyDialog = new Dialog(x, y, width, height);

        if (isCacheValid()) {
            loadFromCache();
        } else {
            fetchSpringInitData();
        }

        this.allDependencies = new DependencyRow[calculateTotalDependencies()];
        populateDependencyArray();

        this.group = new TextField(this.data.groupId().defaultValue());
        this.artifact = new TextField(this.data.artifactId().defaultValue());
        this.packageName = new TextField(this.data.packageName().defaultValue());

        if (doesConfigExist()) {
            loadConfig();
        }

        fillMenuGrid();
    }

    private void calculateContentHeight() {
        int row = 0;

        int logoHeight = 8, projectHeight = 4, languageHeight = 4, bootVersionHeight = 4, projectMetaDataHeight = 2,
                groupHeight = 2,
                artifactHeight = 2, packageNameHeight = 2, packagingHeight = 4,
                configurationHeight = 4, javaVersionHeight = 4,
                addDepHeight = 1, generateHeight = 1, postGenHeight = 3;

        this.logoL = new SectionLayout(row, logoHeight);
        row += this.logoL.height();

        this.project = new SectionLayout(row, projectHeight);
        row += this.project.height();

        this.language = new SectionLayout(row, languageHeight);
        row += this.language.height();

        this.bootVersion = new SectionLayout(row, bootVersionHeight);
        row += this.bootVersion.height();

        this.projectMetaData = new SectionLayout(row, projectMetaDataHeight);
        row += this.projectMetaData.height();

        this.groupL = new SectionLayout(row, groupHeight);
        row += this.groupL.height();

        this.artifactL = new SectionLayout(row, artifactHeight);
        row += this.artifactL.height();

        this.packageNameL = new SectionLayout(row, packageNameHeight);
        row += this.packageNameL.height();

        this.packaging = new SectionLayout(row, packagingHeight);
        row += this.packaging.height();

        this.configuration = new SectionLayout(row, configurationHeight);
        row += this.configuration.height();

        this.javaVersion = new SectionLayout(row, javaVersionHeight);
        row += this.javaVersion.height();

        this.addDep = new SectionLayout(row, addDepHeight);
        row += this.addDep.height();

        this.generate = new SectionLayout(row, generateHeight);
        row += this.generate.height();

        this.postGen = new SectionLayout(row, postGenHeight);
        row += this.postGen.height();

        this.sections = new SectionLayout[] {
                this.project,
                this.language,
                this.bootVersion,
                this.groupL,
                this.artifactL,
                this.packageNameL,
                this.packaging,
                this.configuration,
                this.javaVersion,
                this.addDep,
                this.generate,
                this.postGen
        };
    }

    private boolean isCacheValid() {
        if (Files.exists(this.cacheFile)) {
            this.cache = mapper.readValue(this.cacheFile.toFile(), MetaDataCache.class);

            if (Instant.now().getEpochSecond() - this.cache.timestamp() < 86400) {
                return true;
            }
        }

        return false;
    }

    private void loadFromCache() {
        this.data = this.cache.data();
        this.dependencies = this.cache.dependencies();

        removeUnsupportedProjectTypes();
    }

    private void fetchSpringInitData() {
        try {
            URL url = new URI("https://start.spring.io").toURL();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            con.setRequestMethod("GET");
            con.setRequestProperty("Accept", "application/vnd.initializr.v2.3+json");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);

            int status = con.getResponseCode();

            InputStream stream = status > 299 ? con.getErrorStream() : con.getInputStream();

            JsonNode json = this.mapper.readTree(stream);

            this.data = this.mapper.treeToValue(json, MetaData.class);
            this.dependencies = this.mapper.treeToValue(json.get("dependencies"), Dependencies.class);

            this.cache = new MetaDataCache(Instant.now().getEpochSecond(), this.data, this.dependencies);

            Files.createDirectories(this.cacheDir);

            this.mapper.writerWithDefaultPrettyPrinter().writeValue(this.cacheFile, this.cache);

            removeUnsupportedProjectTypes();

            con.disconnect();
        } catch (MalformedURLException e) {
            IO.println("Malformed URL: " + e.getMessage());
        } catch (URISyntaxException e) {
            IO.println("UriSyntaxException: " + e.getMessage());
        } catch (IOException e) {
            IO.println("IOException: " + e.getMessage());
        }
    }

    private void removeUnsupportedProjectTypes() {
        // remove gradle-build and maven-build
        this.data.type().values().remove(2);
        this.data.type().values().remove(3);
    }

    private void loadConfig() {
        this.config = this.mapper.readValue(this.configFile, MetaDataConfig.class);

        int typeIndex = getSelectionIndex(this.data.type().values(), this.config.type());
        this.cursorX = typeIndex;
        this.currentSelection[0] = typeIndex;
        this.currentSelection[1] = getSelectionIndex(this.data.language().values(), this.config.language());
        this.currentSelection[2] = getSelectionIndex(this.data.bootVersion().values(), this.config.bootVersion());

        this.group.setText(this.config.project().group());
        this.artifact.setText(this.config.project().artifact());
        this.packageName.setText(this.config.project().packageName());

        this.currentSelection[6] = getSelectionIndex(this.data.packaging().values(), this.config.packaging());
        this.currentSelection[7] = getSelectionIndex(this.data.configurationFileFormat().values(),
                this.config.configurationFileFormat());
        this.currentSelection[8] = getSelectionIndex(this.data.javaVersion().values(), this.config.javaVersion());
    }

    private int getSelectionIndex(List<MetaDataOption> options, String value) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).name().equals(value)) {
                return i;
            }
        }

        return 0;
    }

    private boolean doesConfigExist() {
        return Files.exists(this.configFile);
    }

    private void generateProject() {
        saveCurrentSelection();

        try {
            StringBuilder builder = new StringBuilder();

            builder.append("https://start.spring.io/starter.zip?")
                    .append("type=").append(this.data.type().values().get(this.currentSelection[0]).id())
                    .append("&language=").append(this.data.language().values().get(this.currentSelection[1]).id())
                    .append("&bootVersion=").append(this.data.bootVersion().values().get(this.currentSelection[2]).id())
                    .append("&baseDir=").append(this.artifact.getText())
                    .append("&groupId=").append(this.group.getText())
                    .append("&artifactId=").append(this.artifact.getText())
                    .append("&packageName=").append(this.packageName.getText())
                    .append("&packaging=").append(this.data.packaging().values().get(this.currentSelection[6]).id())
                    .append("&javaVersion=").append(this.data.javaVersion().values().get(this.currentSelection[8]).id())
                    .append("&configurationFileFormat=")
                    .append(this.data.configurationFileFormat().values().get(this.currentSelection[7]).id());

            URL url = URI.create(builder.toString()).toURL();

            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            con.setRequestMethod("GET");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);

            int status = con.getResponseCode();

            if (status >= 300) {
                try (InputStream errorStream = con.getErrorStream()) {
                    StringBuilder builderError = new StringBuilder();

                    String error = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);

                    builderError.append(RED)
                            .append("Error: ")
                            .append(RESET_COLOR)
                            .append(error);

                    debug(builderError);
                    return;
                }
            }

            Path cwd = Path.of(System.getProperty("user.dir"));
            Path projectDir = cwd.resolve(this.artifact.getText());

            if (Files.exists(projectDir)) {
                StringBuilder builderError = new StringBuilder();

                String message = "Directory already exists: " + projectDir
                        + " Hint: Artifact needs to be unique in this directory.";

                builderError.append(RED)
                        .append("Error: ")
                        .append(RESET_COLOR)
                        .append(message);

                debug(builderError);
                return;
            }

            Files.createDirectories(projectDir);

            try (InputStream stream = con.getInputStream();
                    ZipInputStream zip = new ZipInputStream(stream)) {

                ZipEntry entry;

                while ((entry = zip.getNextEntry()) != null) {
                    String entryName = entry.getName();

                    if (entryName.startsWith(this.artifact.getText() + "/")) {
                        entryName = entryName.substring(this.artifact.getText().length() + 1);
                    }

                    Path output = projectDir.resolve(entryName);

                    if (entry.isDirectory()) {
                        Files.createDirectories(output);
                    } else {
                        Files.createDirectories(output.getParent());
                        Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
                    }

                    zip.closeEntry();
                }
            }
        } catch (MalformedURLException e) {
            StringBuilder builderError = new StringBuilder();

            builderError.append(RED)
                    .append("Error: ")
                    .append(RESET_COLOR)
                    .append(e.getMessage());

            debug(builderError);
            return;
        } catch (IOException e) {
            StringBuilder builderError = new StringBuilder();

            builderError.append(RED)
                    .append("Error: ")
                    .append(RESET_COLOR)
                    .append(e.getMessage());

            debug(builderError);
            return;
        }

        StringBuilder builderSuccess = new StringBuilder();

        builderSuccess.append(GREEN)
                .append("Success: ")
                .append(RESET_COLOR)
                .append("Created project '")
                .append(this.artifact.getText())
                .append("'.");

        debug(builderSuccess);

        postGenerationMenu();
    }

    private void postGenerationMenu() {
        this.postGenMenuIndex = 0;
        this.isPostGenMenuRunning = true;

        StringBuilder builder = new StringBuilder();
        builder.append("\033[1A");
        renderGenerateButton(builder);
        IO.print(builder);

        renderPostGenerationOptions();

        while (isPostGenMenuRunning) {
            int key = readKey();

            switch (key) {
                case '\r', '\n' -> {
                    if (this.postGenMenuIndex == 0) {
                        this.isPostGenMenuRunning = false;
                        quit();
                    } else {
                        this.isPostGenMenuRunning = false;
                        removePostGenerationOptions();
                    }

                    return;
                }
                case 'A' -> {
                    if (this.postGenMenuIndex == 1) {
                        this.postGenMenuIndex = 0;
                    }
                }
                case 'B' -> {
                    if (this.postGenMenuIndex == 0) {
                        this.postGenMenuIndex = 1;
                    }
                }
                case 'q', -1, 3 -> {
                    this.isPostGenMenuRunning = false;
                    quit();
                }
            }
            renderPostGenerationOptions();
        }
    }

    private void renderPostGenerationOptions() {
        saveCursor();

        StringBuilder builder = new StringBuilder();

        builder.append("\r\n\r\n");

        if (this.postGenMenuIndex == 0) {
            builder.append(GREEN)
                    .append("> Exit")
                    .append(RESET_COLOR)
                    .append("\r\n")
                    .append("  Stay open");
        } else {
            builder.append("  Exit")
                    .append("\r\n")
                    .append(GREEN)
                    .append("> Stay open")
                    .append(RESET_COLOR);
        }

        IO.print(builder);

        restoreCursor();
    }

    private void removePostGenerationOptions() {
        saveCursor();

        StringBuilder builder = new StringBuilder();

        builder.append("\r\n\r\n")
                .append("\033[2K")
                .append("\r\n")
                .append("\033[2K");

        IO.print(builder);

        restoreCursor();
    }

    private void fillMenuGrid() {
        int size = 0;

        for (int i = 0; i < this.currentSelection.length; i++) {
            switch (i) {
                case 0 -> {
                    size = this.data.type().values().size();
                }
                case 1 -> {
                    size = this.data.language().values().size();
                }
                case 2 -> {
                    size = this.data.bootVersion().values().size();
                }
                case 3, 4, 5, 9, 10 -> size = 1;
                case 6 -> {
                    size = this.data.packaging().values().size();
                }
                case 7 -> {
                    size = this.data.configurationFileFormat().values().size();
                }
                case 8 -> {
                    size = this.data.javaVersion().values().size();
                }
            }

            List<Integer> list = createRangeList(size);
            this.menuGrid.add(list);
        }
    }

    private List<Integer> createRangeList(int size) {
        List<Integer> list = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            list.add(i);
        }

        return list;
    }

    private int readKey() {
        try {
            int key = System.in.read();

            // \033 = escape character (decimal value 27)
            if (key != '\033') {
                return key;
            }

            if (System.in.available() == 0) {
                return '\033';
            }

            int key2 = System.in.read();
            if (key2 != '[' && key2 != 'O') {
                return key2;
            }

            return System.in.read();
        } catch (IOException e) {
            IO.println("Error reading key");
            return -1;
        }
    }

    private boolean handleKey(int key) {
        switch (key) {
            case 'q', -1, 3 -> {
                quit();
                return true;
            }
            case 'D', 'h', 'C', 'l', 'B', 'j', 'A', 'k' -> {
                move(key);
                return true;
            }
            case 'i', '\r', '\n' -> {
                if (this.cursorY == 9) {
                    addDependencies();
                }

                if (this.cursorY == 10) {
                    move('A');
                    generateProject();
                    return true;
                }

                if ((key == '\r' || key == '\n') && !isTextFieldSelected()) {
                    return false;
                }

                this.isEditing = true;
                renderStatusBar();
                writeTextField();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void quit() {
        this.isRunning = false;
        saveCurrentSelection();
        clearScreen();
        terminal.disableRawMode();
    }

    private void clearScreen() {
        System.out.print("\033[H");

        for (int i = 0; i < viewPortHeight; i++) {
            System.out.print("\033[2K");
            System.out.print("\033[1B");
        }

        System.out.print("\033[H");
    }

    private void move(int key) {
        Direction dir = getDirection(key);

        int newRow = this.cursorY;
        int newCol = this.cursorX;

        switch (dir) {
            case UP -> newRow--;
            case DOWN -> newRow++;
            case LEFT -> newCol--;
            case RIGHT -> newCol++;
        }

        if (isIllegalMove(newRow, newCol)) {
            return;
        }

        this.previousCursorY = this.cursorY;

        this.cursorY = newRow;
        if (newRow != previousCursorY) {
            this.cursorX = currentSelection[newRow];
        } else {
            this.cursorX = newCol;
        }

        updateScrollCursorY();
        updateSelection();
    }

    private SectionLayout getSelectedSection() {
        return this.sections[this.cursorY];
    }

    private void updateScrollCursorY() {
        int sectionRow = getSelectedSection().row();

        if (this.cursorY == 0) {
            this.scrollOffset = 0;
            return;
        }

        this.oldScrollOffset = this.scrollOffset;

        int desiredCursorRow = sectionRow - this.scrollOffset;

        if (desiredCursorRow >= this.viewPortHeight - SCROLL_MARGIN) {
            this.scrollOffset = sectionRow - (this.viewPortHeight - SCROLL_MARGIN - 1);
        } else if (desiredCursorRow < SCROLL_MARGIN) {
            this.scrollOffset = sectionRow - SCROLL_MARGIN;
        }

        this.scrollOffset = Math.max(0, this.scrollOffset);
    }

    private Direction getDirection(int key) {
        return switch (key) {
            case 'D', 'h' -> Direction.LEFT;
            case 'B', 'j' -> Direction.DOWN;
            case 'A', 'k' -> Direction.UP;
            default -> Direction.RIGHT;
        };
    }

    private boolean isIllegalMove(int newRow, int newCol) {
        if (newRow < 0 || newRow >= this.menuGrid.size()) {
            return true;
        } else if (this.cursorY == newRow && (newCol < 0 || newCol >= this.menuGrid.get(newRow).size())) {
            return true;
        } else {
            return false;
        }
    }

    private void updateSelection() {
        System.arraycopy(this.currentSelection, 0, this.previousSelection, 0, this.currentSelection.length);
        this.currentSelection[this.cursorY] = this.cursorX;
    }

    private void saveCurrentSelection() {
        this.config = new MetaDataConfig(
                this.data.type().values().get(this.currentSelection[0]).name(),
                this.data.language().values().get(this.currentSelection[1]).name(),
                this.data.bootVersion().values().get(this.currentSelection[2]).name(),
                new Project(
                        this.group.getText(),
                        this.artifact.getText(),
                        this.packageName.getText()),
                this.data.packaging().values().get(this.currentSelection[6]).name(),
                this.data.configurationFileFormat().values().get(this.currentSelection[7]).name(),
                this.data.javaVersion().values().get(this.currentSelection[8]).name());

        try {
            Files.createDirectories(this.configDir);

            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(this.configFile.toFile(), config);
        } catch (IOException e) {
            debug(e.getMessage());
        }
    }

    private void renderLoading() {
        StringBuilder builder = new StringBuilder();
        builder.append("Loading metadata...");
        debug(builder);
    }

    private VisibleRange getRenderRange(SectionLayout layout) {
        int sectionTop = layout.row();
        int sectionBottom = sectionTop + layout.height();

        int visibleTop = Math.max(sectionTop, this.scrollOffset);
        int visibleBottom = Math.min(sectionBottom, this.scrollOffset + this.viewPortHeight);

        if (visibleTop >= visibleBottom) {
            return null;
        }

        return new VisibleRange(visibleTop - sectionTop, visibleBottom - sectionTop);
    }

    private void renderLogo(StringBuilder builder) {
        VisibleRange range = getRenderRange(this.logoL);

        if (range == null) {
            return;
        }

        for (int row = range.start(); row < range.end(); row++) {
            if (row < this.LOGO.length) {
                String line = this.LOGO[row];

                if (this.isDimmed) {
                    builder.append(BG_DIMMED)
                            .append(DIMMED);
                }

                builder.append(GREEN)
                        .append(line, 0, 35)
                        .append(RESET_COLOR);

                if (this.isDimmed) {
                    builder.append(BG_DIMMED)
                            .append(DIMMED);
                }

                builder.append(line.substring(35));

                if (this.isDimmed) {
                    builder.append(RESET_DIMMED);
                }
            }

            builder.append("\r\n");
        }

        builder.append(RESET_BUTTON_BG)
                .append(RESET_DIMMED);
    }

    private boolean scrollOffsetChanged() {
        return this.oldScrollOffset != this.scrollOffset;
    }

    private void renderUI() {
        hideCursor();

        StringBuilder builder = new StringBuilder();

        if (scrollOffsetChanged()) {
            this.firstRender = true;
            clearScreen();
        }

        IO.print("\033[H");

        renderLogo(builder);
        renderProject(builder);
        renderLanguage(builder);
        renderBootVersion(builder);
        renderProjectMetaData(builder);
        renderPackaging(builder);
        renderConfiguration(builder);
        renderJavaVersion(builder);
        renderAddDependencies(builder);
        renderGenerateButton(builder);

        IO.print(builder);
        this.firstRender = false;

        if (isTextFieldSelected()) {
            positionTextCursor();
            showCursor();
        } else {
            hideCursor();
        }

        this.oldScrollOffset = this.scrollOffset;
    }

    private void renderSelectionRow(StringBuilder builder, String title, List<MetaDataOption> options,
            int selectionIndex, VisibleRange range, SectionLayout layout) {

        String[] lines = new String[layout.height()];
        Arrays.fill(lines, "");

        boolean selectionChanged = currentSelection[selectionIndex] != previousSelection[selectionIndex];
        boolean isPreviousRow = this.previousCursorY == selectionIndex;
        boolean isUnderlined = this.cursorY == selectionIndex;

        if (this.isDimmed) {
            builder.append(BG_DIMMED)
                    .append(DIMMED);
        }

        if (this.firstRender || selectionChanged || cursorY == selectionIndex) {
            lines[0] = title;
            lines[1] = "";
            renderOptions(options, selectionIndex, isUnderlined, lines);
        } else if (isPreviousRow) {
            lines[0] = title;
            lines[1] = " ";
            renderOptions(options, selectionIndex, false, lines);
        } else {
            lines[0] = "";
            lines[1] = "";
        }

        lines[3] = "";

        for (int i = range.start(); i < range.end(); i++) {
            String line = lines[i];
            builder.append(line).append("\r\n");
        }

        if (this.isDimmed) {
            builder.append(RESET_DIMMED);
        }
    }

    private void renderOptions(List<MetaDataOption> options, int selectionIndex,
            boolean isUnderlined, String[] lines) {

        StringBuilder line = new StringBuilder();

        for (int i = 0; i < options.size(); i++) {
            boolean isSelected = i == currentSelection[selectionIndex];

            if (isSelected) {
                if (isUnderlined) {
                    line.append(UNDERLINED);
                }

                line.append(GREEN + SELECTED + " ")
                        .append(options.get(i).name())
                        .append(RESET_COLOR);

                if (isDimmed) {
                    line.append(BG_DIMMED)
                            .append(DIMMED)
                            .append("  ");
                } else {
                    line.append("  ");
                }

                if (isUnderlined) {
                    line.append(RESET_UNDERLINED);
                }
            } else {
                line.append(UNSELECTED)
                        .append(" ")
                        .append(options.get(i).name())
                        .append("  ");
            }
        }
        lines[2] = line.toString();
    }

    private void renderProject(StringBuilder builder) {
        int selectionIndex = 0;

        VisibleRange range = getRenderRange(this.project);

        if (range == null) {
            return;
        }

        renderSelectionRow(builder, "Project", this.data.type().values(), selectionIndex, range, this.project);
    }

    private void renderLanguage(StringBuilder builder) {
        int selectionIndex = 1;

        VisibleRange range = getRenderRange(this.language);

        if (range == null) {
            return;
        }

        renderSelectionRow(builder, "Language", this.data.language().values(), selectionIndex, range, this.language);
    }

    private void renderBootVersion(StringBuilder builder) {
        int selectionIndex = 2;

        VisibleRange range = getRenderRange(this.bootVersion);

        if (range == null) {
            return;
        }

        renderSelectionRow(builder, "Spring Boot", this.data.bootVersion().values(), selectionIndex, range,
                this.bootVersion);
    }

    private void renderProjectMetaData(StringBuilder builder) {
        if (isDimmed) {
            builder.append(BG_DIMMED)
                    .append(DIMMED);
        }

        VisibleRange projectMetaDataRange = getRenderRange(this.projectMetaData);
        if (projectMetaDataRange != null) {
            builder.append("Project Metadata\r\n\r\n");
        }

        VisibleRange groupRange = getRenderRange(this.groupL);

        if (groupRange != null) {
            renderTextField(builder, "Group", this.group, 3, groupRange);
        }

        VisibleRange artifactRange = getRenderRange(this.artifactL);
        if (artifactRange != null) {
            renderTextField(builder, "Artifact", this.artifact, 4, artifactRange);
        }

        VisibleRange packageNameRange = getRenderRange(this.packageNameL);
        if (packageNameRange != null) {
            renderTextField(builder, "Package name", formatPackageName(), 5, packageNameRange);
        }

        if (isDimmed) {
            builder.append(RESET_DIMMED);
        }
    }

    private void renderTextField(StringBuilder builder, String title, TextField field, int selectionIndex,
            VisibleRange range) {
        String[] lines = new String[2];

        StringBuilder line = new StringBuilder();

        line.append("\033[2K");
        line.append(String.format("    - %-14s: ", title));

        if (selectionIndex == this.cursorY) {
            line.append("[  ");
        }

        line.append(field.getText());

        if (selectionIndex == this.cursorY) {
            line.append("  ]");
        }

        lines[0] = line.toString();
        lines[1] = "";

        for (int i = range.start(); i < range.end(); i++) {
            builder.append(lines[i]).append("\r\n");
        }
    }

    private void writeTextField() {
        showCursor();

        StringBuilder builder = new StringBuilder(getSelectedText(this.cursorY));
        int cursorIdx = 0;

        while (this.isEditing) {
            int key = readKey();

            switch (key) {
                case 'A', 'B', '\033', '\r', '\n' -> {
                    this.isEditing = false;
                    move(key);
                }
                case 'C', 'D' -> {
                    int result = moveCursor(key, cursorIdx);
                    if (result != -1) {
                        cursorIdx = result;
                    }
                }
                case 127 -> cursorIdx = deleteChar(builder, cursorIdx);
                default -> {
                    cursorIdx = writeText(builder, cursorIdx, key);
                }
            }
        }
        hideCursor();
    }

    private int writeText(StringBuilder builder, int cursorIdx, int key) {
        builder.insert(cursorIdx, (char) key);
        cursorIdx++;
        IO.print("\033[1C");
        applyEdit(builder);
        return cursorIdx;
    }

    private int deleteChar(StringBuilder builder, int cursorIdx) {
        if (cursorIdx == builder.length() && cursorIdx > 0) {
            builder.deleteCharAt(cursorIdx - 1);
            cursorIdx--;
            IO.print("\033[1D");
        } else if (cursorIdx > 0 && cursorIdx < builder.length()) {
            builder.deleteCharAt(cursorIdx - 1);
            cursorIdx--;
            IO.print("\033[1D");
        }

        applyEdit(builder);

        return cursorIdx;
    }

    private void applyEdit(StringBuilder builder) {
        switch (this.cursorY) {
            case 3 -> {
                this.group.setText(builder.toString());
                this.updatePackageName = true;
            }
            case 4 -> {
                this.artifact.setText(builder.toString());
                this.updatePackageName = true;
            }
            default -> {
                this.packageName.setText(builder.toString());
                this.updatePackageName = false;
            }
        }

        renderEdit(builder);
    }

    private void renderEdit(StringBuilder builder) {
        saveCursor();
        positionTextCursor();
        IO.print("\033[0K");
        IO.print(builder);
        IO.print("  ]");
        restoreCursor();
    }

    private String getSelectedText(int index) {
        return switch (index) {
            case 3 -> this.group.getText();
            case 4 -> this.artifact.getText();
            default -> this.packageName.getText();
        };
    }

    private boolean isTextFieldSelected() {
        return this.cursorY >= 3 && this.cursorY <= 5;
    }

    private void positionTextCursor() {
        SectionLayout section = getSelectedSection();

        int row = Math.max(section.row() - this.scrollOffset, 0) + 1;

        IO.print("\033[" + row + ";" + TEXT_START + "H");
    }

    private TextField formatPackageName() {
        if (updatePackageName) {
            this.packageName.setText(this.group.getText() + "." + this.artifact.getText());
        }
        return this.packageName;
    }

    int moveCursor(int c, int cursorIdx) {
        if (isIllegalCursorMove(c)) {
            return -1;
        }

        switch ((char) c) {
            case 'A' -> IO.print("\033[1A");
            case 'B' -> IO.print("\033[1B");
            case 'C' -> {
                IO.print("\033[1C");
                cursorIdx++;
            }
            case 'D' -> {
                IO.print("\033[1D");
                cursorIdx--;
            }
        }

        return cursorIdx;
    }

    // for the main spring-initializr-tui ui
    private boolean isIllegalCursorMove(int c) {
        this.textCursorPos = getCursorPosition();

        int textLength = 0;

        switch (this.cursorY) {
            case 3 -> textLength = this.group.getText().length() - 1;
            case 4 -> textLength = this.artifact.getText().length() - 1;
            case 5 -> textLength = this.packageName.getText().length() - 1;
        }

        switch (c) {
            case 'D' -> {
                if (this.textCursorPos.col() <= TEXT_START - 1) {
                    return true;
                }
            }
            case 'C' -> {
                if (this.textCursorPos.col() >= TEXT_START + textLength) {
                    return true;
                }
            }
        }

        return false;
    }

    private CursorPosition getCursorPosition() {
        int row = 0, col = 0;
        int c;

        IO.print("\033[6n");
        System.out.flush();

        try {
            if (System.in.read() != '\033') {
                throw new IOException("Expected ESC character");
            }

            if (System.in.read() != '[') {
                throw new IOException("Expected [");
            }

            while ((c = System.in.read()) != ';') {
                row = row * 10 + (c - '0');
            }

            while ((c = System.in.read()) != 'R') {
                col = col * 10 + (c - '0');
            }

        } catch (IOException e) {
            IO.print(e);
        }

        return new CursorPosition(row - 1, col - 1);
    }

    private void saveCursor() {
        IO.print("\033[s");
    }

    private void restoreCursor() {
        IO.print("\033[u");
    }

    private void renderPackaging(StringBuilder builder) {
        int selectionIndex = 6;

        VisibleRange range = getRenderRange(this.packaging);

        if (range == null) {
            return;
        }

        renderSelectionRow(builder, "Packaging", this.data.packaging().values(), selectionIndex, range, this.packaging);
    }

    private void renderConfiguration(StringBuilder builder) {
        int selectionIndex = 7;

        VisibleRange range = getRenderRange(this.configuration);

        if (range == null) {
            return;
        }

        renderSelectionRow(builder, "Configuration", this.data.configurationFileFormat().values(),
                selectionIndex, range, this.configuration);
    }

    private void renderJavaVersion(StringBuilder builder) {
        int selectionIndex = 8;

        VisibleRange range = getRenderRange(this.javaVersion);

        if (range == null) {
            return;
        }

        renderSelectionRow(builder, "Java", this.data.javaVersion().values(), selectionIndex, range, this.javaVersion);
    }

    private void renderAddDependencies(StringBuilder builder) {
        VisibleRange range = getRenderRange(this.addDep);

        if (range == null) {
            return;
        }

        if (this.isDimmed) {
            builder.append(BG_DIMMED)
                    .append(DIMMED);
        }

        builder.append("\r\n");

        if (this.cursorY == 9) {
            builder.append(BUTTON_BG_SELECTED)
                    .append(" Add dependencies ")
                    .append(RESET_COLOR);
        } else {
            builder.append(BUTTON_BG_UNSELECTED)
                    .append(" Add dependencies ");
        }

        builder.append(RESET_BUTTON_BG)
                .append("\r\n");

        if (this.isDimmed) {
            builder.append(RESET_DIMMED);
        }
    }

    private void renderGenerateButton(StringBuilder builder) {
        VisibleRange range = getRenderRange(this.generate);

        if (range == null) {
            return;
        }

        if (this.isDimmed) {
            builder.append(BG_DIMMED)
                    .append(DIMMED);
        }

        builder.append("\r\n");

        if (this.cursorY == 10) {
            builder.append(BUTTON_BG_SELECTED)
                    .append(" Generate ")
                    .append(RESET_COLOR);
        } else {
            builder.append(BUTTON_BG_UNSELECTED)
                    .append(" Generate ");
        }

        builder.append(RESET_BUTTON_BG);

        if (this.isDimmed) {
            builder.append(RESET_DIMMED);
        }
    }

    private void renderStatusBar() {
        saveCursor();

        StringBuilder builder = new StringBuilder();

        if (isDimmed) {
            builder.append(BG_DIMMED).append(DIMMED);
        }

        String mode, hints = "↑↓ Navigate   ←→ Change   Enter Edit   Esc Back   Ctrl+C Exit",
                v = "   v" + this.version;

        if (this.isEditing) {
            mode = "INSERT";
        } else {
            mode = "NORMAL";
        }

        int spaces = this.columns - mode.length() - hints.length() - v.length();

        builder.append("\033[" + String.valueOf(this.rows - 1) + ";0H");
        builder.append(mode);

        if (spaces < 1) {
            builder.append(" ");
        } else {
            builder.append(" ".repeat(spaces));
        }

        builder.append(hints).append(v);

        if (isDimmed) {
            builder.append(RESET_DIMMED);
        }

        IO.print(builder);
        restoreCursor();
    }

    private void hideCursor() {
        IO.print("\033[?25l");
    }

    private void showCursor() {
        IO.print("\033[?25h");
    }

    private <T> void debug(T value) {
        saveCursor();

        StringBuilder builder = new StringBuilder();
        builder.append("\033[" + String.valueOf(this.rows) + ";0H")
                .append("\033[2K");

        if (this.isDimmed) {
            builder.append(BG_DIMMED)
                    .append(DIMMED);
        }

        builder.append(" ".repeat(this.columns));

        builder.append("\033[" + String.valueOf(this.rows) + ";0H");

        builder.append(value);

        builder.append(RESET_DIMMED);

        IO.print(builder);
        restoreCursor();
    }

    // ---------------------- Dependency Dialog Window ------------------

    private void addDependencies() {
        this.isAddDependencyRunning = true;
        this.dependencyFirstRender = true;
        this.depCursorY = 0;
        this.previousDepCursorY = 0;
        this.depScrollOffsetY = 0;
        renderDialogBackGround();
        renderDialogWindow();
        renderDialogTitle();
        renderDialog();
        moveDependencyCursorLine();
        showCursor();

        while (isAddDependencyRunning) {
            int key = readKey();

            switch (key) {
                case 'q', '\033' -> {
                    this.tempSelectedDependencies.clear();
                    this.availableDependencies.clear();
                    isAddDependencyRunning = false;
                }
                case 'A', 'B', 'j', 'k' -> {
                    moveDependencyCursor(key);
                    moveDependencyCursorLine();

                    if (this.scrollOffsetChanged) {
                        rerenderDependencies();
                    }

                    saveCursor();
                    StringBuilder builder = new StringBuilder();
                    renderDialogStatusBar(builder);
                    IO.print(builder);
                    restoreCursor();
                }
                case ' ' -> {
                    if (isSelected()) {
                        boolean isDeselectable = deselectDependency();

                        if (isDeselectable) {
                            this.tempSelectedDepListChanged = true;
                        }

                        if (this.tempSelectedDependencies.size() > 1 && isDeselectable) {
                            moveDependencyCursor('B');
                            moveDependencyCursorLine();
                        }

                        if (this.tempSelectedDepListChanged) {
                            rerenderDependencies();
                            this.tempSelectedDepListChanged = false;
                        }
                    } else {
                        boolean isAvailableDepRow = selectDependency();

                        if (isAvailableDepRow) {
                            this.tempSelectedDepListChanged = true;
                        }

                        if (this.tempSelectedDependencies.size() > 1 && isAvailableDepRow) {
                            moveDependencyCursor('B');
                            moveDependencyCursorLine();
                        }

                        if (this.tempSelectedDepListChanged) {
                            rerenderDependencies();
                            this.tempSelectedDepListChanged = false;
                        }
                    }
                }
                case '\r', '\n' -> {
                    saveDependencies();
                    this.tempSelectedDependencies.clear();
                    this.availableDependencies.clear();
                    isAddDependencyRunning = false;
                }
                case 'g' -> {
                    int key2 = readKey();

                    if (key2 == 'g') {
                        this.depCursorY = 0;
                        this.depScrollOffsetY = 0;
                        this.previousDepCursorY = 0;

                        moveDependencyCursorLine();
                        rerenderDependencies();
                    }
                }
                case 'G' -> {
                    int borderRows = 2, statusBarRow = 1, padding = 1;

                    this.depCursorY = this.dependencyDialog.getHeight() - borderRows - statusBarRow - padding - 1;
                    this.depScrollOffsetY = this.DEPENDENCY_MENU_ITEMS.size() - 1 - this.depCursorY;

                    moveDependencyCursorLine();
                    rerenderDependencies();
                }

            }
        }

        removeDialogWindow();
    }

    private boolean selectDependency() {
        int selectedSize = 1, uiRows = 6;
        if (!this.tempSelectedDependencies.isEmpty()) {
            selectedSize = this.tempSelectedDependencies.size();
        }

        // Example: first dependency in the available list "GraalVM Native Support"
        // which would be index 0 with also nothing selected at the moment:
        // index = 7 - 1 - 6 + 0 = 0
        int index = this.depCursorY - selectedSize - uiRows + this.depScrollOffsetY;

        if (index < 0) {
            return false;
        }

        DependencyRow dependencyRow = this.availableDependencies.get(index);

        this.availableDependencies.remove(dependencyRow);

        DependencyRow updatedRow = new DependencyRow(dependencyRow.dependency(), true, dependencyRow.originalIndex());
        this.tempSelectedDependencies.add(updatedRow);

        updateSelectedHeader();
        updateAvailableHeader();

        rebuildDependencyMenu();
        return true;
    }

    private boolean deselectDependency() {
        int selectedDepStartIndex = 4;
        int index = this.depCursorY - selectedDepStartIndex;

        if (index < 0) {
            return false;
        }

        DependencyRow dependencyRow = this.tempSelectedDependencies.get(index);
        this.tempSelectedDependencies.remove(index);

        int originalIndex = dependencyRow.originalIndex();

        int insertIndex = 0;

        while (insertIndex < this.availableDependencies.size()
                && this.availableDependencies.get(insertIndex).originalIndex() < originalIndex) {
            insertIndex++;
        }

        DependencyRow updatedRow = new DependencyRow(dependencyRow.dependency(), false, dependencyRow.originalIndex());
        this.availableDependencies.add(insertIndex - 1, updatedRow);

        updateSelectedHeader();
        updateAvailableHeader();

        rebuildDependencyMenu();
        return true;
    }

    private void saveDependencies() {
        this.selectedDependencies = new ArrayList<>(this.tempSelectedDependencies);
    }

    private void rebuildDependencyMenu() {
        this.DEPENDENCY_MENU_ITEMS.clear();

        this.DEPENDENCY_MENU_ITEMS.add(this.spacer);
        this.DEPENDENCY_MENU_ITEMS.add(this.filter);
        this.DEPENDENCY_MENU_ITEMS.add(this.spacer);
        this.DEPENDENCY_MENU_ITEMS.add(this.selected);

        if (this.tempSelectedDependencies.isEmpty()) {
            this.DEPENDENCY_MENU_ITEMS.add(this.nothingSelected);
        } else {
            for (DependencyRow row : this.tempSelectedDependencies) {
                this.DEPENDENCY_MENU_ITEMS.add(row);
            }
        }

        this.DEPENDENCY_MENU_ITEMS.add(this.spacer);

        this.DEPENDENCY_MENU_ITEMS.add(this.available);

        for (DependencyRow row : this.availableDependencies) {
            this.DEPENDENCY_MENU_ITEMS.add(row);
        }
    }

    private void updateSelectedHeader() {
        this.selected = new Header(List.of(
                new TextSegment("Selected ", WHITE),
                new TextSegment("(" + this.tempSelectedDependencies.size() + ")", BORDER_COLOR)));

        this.DEPENDENCY_MENU_ITEMS.set(3, this.selected);
    }

    private void updateAvailableHeader() {
        this.available = new Header(List.of(
                new TextSegment("Available ", WHITE),
                new TextSegment("(" + (this.availableDependencies.size()) + ")",
                        BORDER_COLOR)));

        this.DEPENDENCY_MENU_ITEMS.set(6, this.available);
    }

    private void rerenderDependencies() {
        StringBuilder builder = new StringBuilder();

        renderDependencies(builder);
        positionDialogCursor(this.depCursorY + 1, 1, builder);

        IO.print(builder);

        this.scrollOffsetChanged = false;
    }

    private void moveDependencyCursor(int key) {
        Direction dir = getDirection(key);

        if (isIllegalDependencyCursorMove(dir)) {
            return;
        }

        this.previousDepCursorY = this.depCursorY;

        switch (dir) {
            case UP -> {
                this.depCursorY--;
            }
            case DOWN -> {
                this.depCursorY++;
            }
            default -> {

            }
        }
    }

    private boolean isIllegalDependencyCursorMove(Direction dir) {
        switch (dir) {
            case UP -> {
                if (this.depCursorY == 0) {
                    return true;
                }

                if (this.depCursorY == SCROLL_DEP_MARGIN && this.depScrollOffsetY > 0) {
                    this.depScrollOffsetY--;
                    this.scrollOffsetChanged = true;
                    return true;
                }
            }
            case DOWN -> {
                int currentIndex = this.depCursorY + this.depScrollOffsetY;
                int futureIndex = currentIndex + 1;

                if (futureIndex == this.DEPENDENCY_MENU_ITEMS.size()) {
                    return true;
                }

                int bottomMargin = this.dependencyDialog.getHeight() - SCROLL_DEP_MARGIN * 2;

                if (this.depCursorY == bottomMargin) {
                    int remainingItems = this.DEPENDENCY_MENU_ITEMS.size() - futureIndex;

                    if (remainingItems > SCROLL_DEP_MARGIN) {
                        this.depScrollOffsetY++;
                        this.scrollOffsetChanged = true;
                        return true;
                    }
                }
            }
            default -> {

            }
        }
        return false;
    }

    private void moveDependencyCursorLine() {
        StringBuilder builder = new StringBuilder();

        DialogRow currRow = this.DEPENDENCY_MENU_ITEMS.get(this.depCursorY + this.depScrollOffsetY);
        boolean isCurrRowHighlighted = isHighlighted(this.depCursorY);

        renderDependencyCursorLine(builder, isCurrRowHighlighted, this.depCursorY + 1);

        if (this.scrollOffsetChanged || this.depCursorY <= this.dependencyDialog.getHeight() - SCROLL_DEP_MARGIN) {
            renderDependencyRow(currRow, builder, this.depCursorY + 1, getOffsetX(currRow),
                    isCurrRowHighlighted, this.depCursorY);
        }

        if (!this.dependencyFirstRender) {
            DialogRow prevRow = this.DEPENDENCY_MENU_ITEMS.get(this.previousDepCursorY + this.depScrollOffsetY);
            boolean isPrevRowHighlighted = isHighlighted(this.previousDepCursorY);

            renderDependencyCursorLine(builder, isPrevRowHighlighted, this.previousDepCursorY + 1);

            if (this.scrollOffsetChanged || this.depCursorY <= this.dependencyDialog.getHeight() - SCROLL_DEP_MARGIN) {
                renderDependencyRow(prevRow, builder, this.previousDepCursorY + 1,
                        getOffsetX(prevRow), isPrevRowHighlighted, this.previousDepCursorY);
            }
        }

        this.dependencyFirstRender = false;
        positionDialogCursor(this.depCursorY + 1, 1, builder);
        IO.print(builder);
    }

    private void renderDependencyCursorLine(StringBuilder builder, boolean isHighlighted, int offsetY) {
        int offsetX = 1;
        positionDialogCursor(offsetY, offsetX, builder);

        if (isHighlighted) {
            builder.append(DEP_LINE_COLOR).append(" ".repeat(this.dependencyDialog.getWidth() - 2))
                    .append(RESET_BUTTON_BG);
        } else {
            builder.append(BG).append(" ".repeat(this.dependencyDialog.getWidth() - 2))
                    .append(RESET_BUTTON_BG);
        }
    }

    private void renderDependencyRow(DialogRow row, StringBuilder builder, int offsetY, int offsetX,
            boolean isHighlighted, int index) {

        if (row instanceof Header header
                && header.segments().getFirst().text().equals(this.nothingSelected.segments().getFirst().text())) {
            offsetX = (this.dependencyDialog.getWidth() - header.segments().getFirst().text().length()) / 2;
        }

        positionDialogCursor(offsetY, offsetX, builder);
        renderDependencyCursorLine(builder, isHighlighted, offsetY);
        positionDialogCursor(offsetY, offsetX, builder);

        if (isHighlighted) {
            builder.append(DEP_LINE_COLOR);
        }

        switch (row) {
            case Spacer _ -> {
                builder.append("");
            }
            case Header header -> {
                for (TextSegment segment : header.segments()) {
                    builder.append(segment.color()).append(segment.text());
                }
                builder.append(RESET_COLOR);
            }
            case DependencyRow depRow -> {
                if (depRow.isSelected()) {
                    builder.append("  ").append(GREEN).append(SELECTED).append(RESET_COLOR);

                    if (isHighlighted) {
                        builder.append(DEP_LINE_COLOR);
                    }

                    builder.append(" ").append(depRow.dependency().name());
                } else {
                    builder.append("  ").append(UNSELECTED).append(" ").append(depRow.dependency().name());
                }
            }
        }

        builder.append(RESET_COLOR);
    }

    private int getOffsetX(DialogRow row) {
        switch (row) {
            case Spacer _ -> {
                return 1;
            }
            case Header _ -> {
                return 2;
            }
            case DependencyRow _ -> {
                return 2;
            }
        }
    }

    private void renderDialog() {
        StringBuilder builder = new StringBuilder();

        fillDialogRows();

        renderDependencies(builder);
        renderDialogStatusBar(builder);

        positionDialogCursor(1, 1, builder);
        IO.print(builder);
    }

    private void renderDependencies(StringBuilder builder) {
        int offsetY = 1, offsetX = 2;

        for (int i = 0; i < this.DEPENDENCY_MENU_ITEMS.size(); i++) {
            if (i == this.dependencyDialog.getHeight() - SCROLL_DEP_MARGIN + 1) {
                break;
            }

            int index = i + this.depScrollOffsetY;

            if (index >= this.DEPENDENCY_MENU_ITEMS.size()) {
                break;
            }

            renderDependencyRow(this.DEPENDENCY_MENU_ITEMS.get(i + this.depScrollOffsetY), builder, offsetY, offsetX,
                    isHighlighted(i), i);
            offsetY++;
        }
    }

    private void positionDialogCursor(int offsetY, int offsetX, StringBuilder builderCursor) {
        builderCursor.append("\033[")
                .append(this.dependencyDialog.getY() + offsetY)
                .append(";")
                .append(this.dependencyDialog.getX() + offsetX)
                .append("H");
    }

    private boolean isHighlighted(int index) {
        return this.depCursorY == index;
    }

    private void renderDialogTitle() {
        StringBuilder builder = new StringBuilder();

        final String title = " Dependencies ";
        int corner = 1;
        int textStart = (this.dependencyDialog.getWidth() - corner * 2 - title.length()) / 2 + corner;

        builder.append(BORDER_COLOR);
        for (int row = 0; row < this.dependencyDialog.getHeight(); row++) {
            positionDialogCursor(row, 0, builder);
            for (int col = 0; col < this.dependencyDialog.getWidth(); col++) {
                if (row == 0) {
                    if (col == 0) {
                        builder.append(TL);
                    } else if (col == this.dependencyDialog.getWidth() - 1) {
                        builder.append(TR);
                    } else if (col == textStart) {
                        builder.append(RESET_COLOR).append(title).append(BORDER_COLOR);
                        col += title.length() - 1;
                    } else {
                        builder.append(H);
                    }
                } else if (row == this.dependencyDialog.getHeight() - 1) {
                    if (col == 0) {
                        builder.append(BL);
                    } else if (col == this.dependencyDialog.getWidth() - 1) {
                        builder.append(BR);
                    } else {
                        builder.append(H);
                    }
                } else {
                    if (col == 0 || col == this.dependencyDialog.getWidth() - 1) {
                        builder.append(V);
                    } else {
                        builder.append(" ");
                    }
                }
            }
        }

        builder.append(RESET_COLOR);
        IO.print(builder);
    }

    private void fillDialogRows() {
        populateTempSelectedDependencyList();
        populateAllDependencyList();

        this.DEPENDENCY_MENU_ITEMS.clear();

        this.spacer = new Spacer();

        this.filter = new Header(List.of(
                new TextSegment("Group Filter: press <C-f> to apply filter", WHITE)));

        this.selected = new Header(List.of(
                new TextSegment("Selected ", WHITE),
                new TextSegment("(" + this.tempSelectedDependencies.size() + ")", BORDER_COLOR)));

        this.available = new Header(List.of(
                new TextSegment("Available ", WHITE),
                new TextSegment("(" + this.availableDependencies.size() + ")", BORDER_COLOR)));

        this.nothingSelected = new Header(List.of(
                new TextSegment("No dependencies.", BORDER_COLOR)));

        this.DEPENDENCY_MENU_ITEMS.add(spacer);
        this.DEPENDENCY_MENU_ITEMS.add(filter);
        this.DEPENDENCY_MENU_ITEMS.add(spacer);
        this.DEPENDENCY_MENU_ITEMS.add(selected);

        if (this.selectedDependencies.isEmpty()) {
            this.DEPENDENCY_MENU_ITEMS.add(nothingSelected);
        } else {
            for (DependencyRow row : this.selectedDependencies) {
                this.DEPENDENCY_MENU_ITEMS.add(row);
            }
        }

        this.DEPENDENCY_MENU_ITEMS.add(spacer);
        this.DEPENDENCY_MENU_ITEMS.add(available);

        for (DependencyRow row : this.availableDependencies) {
            this.DEPENDENCY_MENU_ITEMS.add(row);
        }
    }

    private void populateTempSelectedDependencyList() {
        if (this.selectedDependencies.isEmpty()) {
            return;
        }

        this.tempSelectedDependencies = new ArrayList<>(this.selectedDependencies);
    }

    private void populateAllDependencyList() {
        this.availableDependencies = new ArrayList<>();

        int originalIndex = 0;

        for (DependencyGroup group : this.dependencies.values()) {
            for (Dependency dep : group.values()) {
                DependencyRow depRow = new DependencyRow(dep, false, originalIndex);

                if (this.tempSelectedDependencies.stream().anyMatch(row -> row.dependency().equals(dep))) {
                    continue;
                }

                this.availableDependencies.add(depRow);

                originalIndex++;
            }
        }
    }

    private int calculateTotalDependencies() {
        int counter = 0;

        for (DependencyGroup group : this.dependencies.values()) {
            for (Dependency _ : group.values()) {
                counter++;
            }
        }

        return counter;
    }

    private void populateDependencyArray() {
        int index = 0;

        for (DependencyGroup group : this.dependencies.values()) {
            for (Dependency dep : group.values()) {
                this.allDependencies[index] = new DependencyRow(dep, false, index);
                index++;
            }
        }

    }

    private void renderDialogStatusBar(StringBuilder builder) {
        boolean isSelected = isSelected();

        String hintsSelected = "↑↓ Navigate  Space Deselect  gg Start  G End  Enter Save  Esc Cancel";
        String hintsUnselected = "↑↓ Navigate  Space Select  gg Start  G End  Enter Save  Esc Cancel";
        String hints;

        if (isSelected) {
            hints = hintsSelected;
        } else {
            hints = hintsUnselected;
        }

        int border = 1, space = (this.dependencyDialog.getWidth() - border * 2 - hints.length()) / 2;

        positionDialogCursor(this.dependencyDialog.getHeight() - 2, 1, builder);
        builder.append(" ".repeat(space)).append(hints).append(" ".repeat(space));
    }

    private boolean isSelected() {
        if (this.tempSelectedDependencies.isEmpty()) {
            return false;
        }

        int selectedDepStartIndex = 4;

        return this.depCursorY >= selectedDepStartIndex
                && this.depCursorY < selectedDepStartIndex + this.tempSelectedDependencies.size();
    }

    private void renderDialogBackGround() {
        StringBuilder builderDimmed = new StringBuilder();

        builderDimmed.append("\033[H");

        // Dim entire terminal
        for (int row = 1; row <= this.rows; row++) {
            builderDimmed.append("\033[")
                    .append(row)
                    .append(";1H");

            builderDimmed.append(BG_DIMMED)
                    .append(" ".repeat(this.columns));
        }

        IO.print(builderDimmed);

        this.isDimmed = true;
        this.firstRender = true;
        renderUI();
        renderStatusBar();
        this.isDimmed = false;
    }

    private void renderDialogWindow() {
        StringBuilder builderDialog = new StringBuilder();

        for (int row = 0; row < this.dependencyDialog.getHeight(); row++) {
            builderDialog.append("\033[")
                    .append(this.dependencyDialog.getY() + row)
                    .append(";")
                    .append(this.dependencyDialog.getX())
                    .append("H");

            builderDialog.append(BG)
                    .append(" ".repeat(this.dependencyDialog.getWidth()))
                    .append(RESET_BUTTON_BG);
        }

        IO.print(builderDialog);
    }

    private void removeDialogWindow() {
        StringBuilder builder = new StringBuilder();

        builder.append("\033[H");

        // undim entire terminal
        for (int row = 1; row <= this.rows; row++) {
            builder.append("\033[")
                    .append(row)
                    .append(";1H");

            builder.append(BG)
                    .append(" ".repeat(this.columns))
                    .append(RESET_BUTTON_BG);
        }

        for (int row = 0; row < dependencyDialog.getHeight(); row++) {
            builder.append("\033[")
                    .append(this.dependencyDialog.getY() + row)
                    .append(";")
                    .append(this.dependencyDialog.getX())
                    .append("H");

            builder.append(BG)
                    .append(" ".repeat(this.dependencyDialog.getWidth()))
                    .append(RESET_BUTTON_BG);
        }

        IO.print(builder);
        this.firstRender = true;
        renderUI();
        renderStatusBar();
    }

}
