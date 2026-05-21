/*
 * JavaFX View for the main window (MVP).
 *
 * Adds (1.1):
 *  - Ignored items list (ListView + Add file / Add folder / Remove)
 *  - Tabbed layout: Pipeline | Ignored items | Help/About
 *  - "Organize by" includes First letter
 *  - 7-Zip status + install button
 */
package com.archivemachine.mvp;

import com.archivemachine.core.destination.DestinationMode;
import com.archivemachine.core.settings.CoreSettings;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class MainView implements MainViewContract {

    private static final String GITHUB_URL = "https://github.com/code-Redot";

    private final Stage stage;
    private final Parent root;

    // region inputs
    private final TextField sourceField = new TextField();
    private final TextField destinationField = new TextField();
    private final ComboBox<DestinationMode> destinationModeBox = new ComboBox<>();
    private final CheckBox decompressCheck = new CheckBox("Decompress archives (.rar/.7z/.zip)");
    private final CheckBox skipShortcutsCheck = new CheckBox("Skip shortcuts (.lnk / .url / symlinks)");
    private final Spinner<Integer> partitionLimitSpinner = new Spinner<>(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 1_000_000, 0));
    private final TextField sevenZipField = new TextField("7z");

    // region ignore list
    private final ObservableList<String> ignoreItems = FXCollections.observableArrayList();
    private final ListView<String> ignoreList = new ListView<>(ignoreItems);

    // region outputs
    private final Label statusLabel = new Label("Ready");
    private final Label activeTaskLabel = new Label("");
    private final ProgressBar progressBar = new ProgressBar(0.0);

    // region actions
    private final Button startBtn = new Button("Start");
    private final Button pauseBtn = new Button("Pause");
    private final Button resumeBtn = new Button("Resume");
    private final Button cancelBtn = new Button("Cancel");
    private final Button resetBtn = new Button("Reset");
    private final Button addIgnoreFileBtn = new Button("Add file...");
    private final Button addIgnoreFolderBtn = new Button("Add folder...");
    private final Button removeIgnoreBtn = new Button("Remove selected");

    // presenter callbacks
    private Runnable onStart = () -> {};
    private Runnable onPause = () -> {};
    private Runnable onResume = () -> {};
    private Runnable onCancel = () -> {};
    private Runnable onReset = () -> {};
    private Runnable onAddIgnoreFile = () -> {};
    private Runnable onAddIgnoreFolder = () -> {};
    private Consumer<String> onRemoveIgnoreSelected = s -> {};

    public MainView(Stage stage) {
        this.stage = stage;

        // ---- header bar (info button) -------------------------------------
        Button infoBtn = new Button("ℹ");
        infoBtn.setFocusTraversable(false);
        infoBtn.setTooltip(new Tooltip("About"));
        infoBtn.setOnAction(e -> showAboutDialog());

        // ---- pipeline tab --------------------------------------------------
        Button srcBtn = new Button("Browse...");
        Button dstBtn = new Button("Browse...");
        Button sevenZipBtn = new Button("Browse...");

        srcBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            File f = dc.showDialog(stage);
            if (f != null) sourceField.setText(f.getAbsolutePath());
        });
        dstBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            File f = dc.showDialog(stage);
            if (f != null) destinationField.setText(f.getAbsolutePath());
        });

        destinationModeBox.getItems().setAll(DestinationMode.values());
        destinationModeBox.getSelectionModel().select(DestinationMode.TRANSFER_DATE);

        sevenZipField.disableProperty().bind(decompressCheck.selectedProperty().not());
        sevenZipBtn.disableProperty().bind(decompressCheck.selectedProperty().not());

        sevenZipBtn.setOnAction(e -> pickSevenZip());

        startBtn.setOnAction(e -> onStart.run());
        pauseBtn.setOnAction(e -> onPause.run());
        resumeBtn.setOnAction(e -> onResume.run());
        cancelBtn.setOnAction(e -> onCancel.run());
        resetBtn.setOnAction(e -> onReset.run());

        partitionLimitSpinner.setEditable(true);
        partitionLimitSpinner.setPrefWidth(140);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(18);

        GridPane pipelineGrid = new GridPane();
        pipelineGrid.setHgap(10);
        pipelineGrid.setVgap(10);
        pipelineGrid.setPadding(new Insets(14));
        ColumnConstraints c0 = new ColumnConstraints(); c0.setMinWidth(170);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setMinWidth(Region.USE_PREF_SIZE);
        pipelineGrid.getColumnConstraints().addAll(c0, c1, c2);

        int row = 0;
        pipelineGrid.add(new Label("Source directory:"), 0, row);
        pipelineGrid.add(sourceField, 1, row);
        pipelineGrid.add(srcBtn, 2, row++);

        pipelineGrid.add(new Label("Destination directory:"), 0, row);
        pipelineGrid.add(destinationField, 1, row);
        pipelineGrid.add(dstBtn, 2, row++);

        pipelineGrid.add(new Label("Organize by:"), 0, row);
        pipelineGrid.add(destinationModeBox, 1, row++);

        pipelineGrid.add(decompressCheck, 1, row++);
        pipelineGrid.add(skipShortcutsCheck, 1, row++);

        pipelineGrid.add(new Label("Partition item limit:"), 0, row);
        pipelineGrid.add(partitionLimitSpinner, 1, row++);

        pipelineGrid.add(new Label("7-Zip executable:"), 0, row);
        pipelineGrid.add(sevenZipField, 1, row);
        pipelineGrid.add(sevenZipBtn, 2, row++);

        HBox buttonsBox = new HBox(8, startBtn, pauseBtn, resumeBtn, cancelBtn, resetBtn);
        buttonsBox.setAlignment(Pos.CENTER_LEFT);
        pipelineGrid.add(buttonsBox, 0, row++, 3, 1);

        pipelineGrid.add(progressBar, 0, row++, 3, 1);
        pipelineGrid.add(new Label("Current task:"), 0, row++, 3, 1);
        pipelineGrid.add(activeTaskLabel, 0, row++, 3, 1);
        pipelineGrid.add(new Label("Status:"), 0, row++, 3, 1);
        pipelineGrid.add(statusLabel, 0, row, 3, 1);

        // ---- ignored tab ---------------------------------------------------
        ignoreList.setPlaceholder(new Label("No items ignored. Use the buttons below to add files or folders."));
        ignoreList.setPrefHeight(220);

        addIgnoreFileBtn.setOnAction(e -> onAddIgnoreFile.run());
        addIgnoreFolderBtn.setOnAction(e -> onAddIgnoreFolder.run());
        removeIgnoreBtn.setOnAction(e -> onRemoveIgnoreSelected.accept(ignoreList.getSelectionModel().getSelectedItem()));
        removeIgnoreBtn.disableProperty().bind(ignoreList.getSelectionModel().selectedItemProperty().isNull());

        HBox ignoreButtons = new HBox(8, addIgnoreFileBtn, addIgnoreFolderBtn, removeIgnoreBtn);
        ignoreButtons.setAlignment(Pos.CENTER_LEFT);

        VBox ignoreBox = new VBox(10,
                new Label("These items will be skipped during the next run."),
                ignoreList,
                ignoreButtons);
        ignoreBox.setPadding(new Insets(14));

        // ---- help tab ------------------------------------------------------
        VBox helpBox = new VBox(10,
                bold("ArchiveMachine 1.1"),
                new Label("Transfers level-0 items from a source directory to an organized destination."),
                separator(),
                bold("Organize-by modes"),
                new Label("• Transfer date — today's MMM yyyy"),
                new Label("• Creation date — the item's creation time"),
                new Label("• Last modified — the item's last write time"),
                new Label("• First letter — A..Z / 0-9 / # bucket"),
                separator(),
                bold("Partitioning"),
                new Label("Limit > 0 caps items per bucket. On re-run, partitions resume at (max P + 1)."),
                separator(),
                bold("7-Zip"),
                new Label("On first run without 7-Zip installed, you'll be prompted to install it silently."),
                separator(),
                bold("Credits"),
                aboutLinkRow(GITHUB_URL));
        helpBox.setPadding(new Insets(14));

        // ---- tab pane ------------------------------------------------------
        Tab pipelineTab = new Tab("Pipeline", pipelineGrid);
        Tab ignoreTab = new Tab("Ignored items", ignoreBox);
        Tab helpTab = new Tab("Help / About", helpBox);
        for (Tab t : List.of(pipelineTab, ignoreTab, helpTab)) t.setClosable(false);

        TabPane tabs = new TabPane(pipelineTab, ignoreTab, helpTab);
        tabs.setPrefWidth(640);

        BorderPane top = new BorderPane();
        top.setPadding(new Insets(6, 10, 0, 0));
        top.setRight(infoBtn);

        BorderPane shell = new BorderPane();
        shell.setTop(top);
        shell.setCenter(tabs);

        this.root = shell;
    }

    private void pickSevenZip() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select 7-Zip Executable");

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            fc.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("Executable (*.exe)", "*.exe"));
        } else {
            fc.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("7-Zip (7z, 7za, 7zz)", "7z", "7za", "7zz"));
        }

        String current = sevenZipField.getText();
        if (current != null && !current.isBlank()) {
            File f = new File(current.trim());
            if (f.isAbsolute() && f.exists()) {
                File dir = f.isDirectory() ? f : f.getParentFile();
                if (dir != null && dir.exists()) fc.setInitialDirectory(dir);
            }
        }

        File selected = fc.showOpenDialog(stage);
        if (selected == null) return;

        String name = selected.getName();
        boolean valid = isWindows
                ? (name.equalsIgnoreCase("7z.exe") || name.equalsIgnoreCase("7za.exe") || name.equalsIgnoreCase("7zz.exe"))
                : (name.equalsIgnoreCase("7z") || name.equalsIgnoreCase("7za") || name.equalsIgnoreCase("7zz"));

        if (!valid) {
            showError("Selected file is not a supported 7-Zip executable.\n\n"
                    + "Allowed: " + (isWindows ? "7z.exe, 7za.exe, 7zz.exe" : "7z, 7za, 7zz"));
            return;
        }
        sevenZipField.setText(selected.getAbsolutePath());
    }

    private static Label bold(String s) {
        Label l = new Label(s);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    private static Separator separator() { return new Separator(); }

    private HBox aboutLinkRow(String url) {
        Hyperlink link = new Hyperlink(url);
        link.setOnAction(e -> openExternalLink(url));
        HBox row = new HBox(6, new Label("GitHub:"), link);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void openExternalLink(String url) {
        if (url == null || url.isBlank()) return;
        try {
            if (!Desktop.isDesktopSupported()) {
                showError("Desktop browsing is not supported on this system.");
                return;
            }
            Desktop.getDesktop().browse(URI.create(url.trim()));
        } catch (Exception ex) {
            showError("Failed to open link: " + ex.getMessage());
        }
    }

    private void showAboutDialog() {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("About");
        a.setHeaderText("ArchiveMachine 1.1");

        Label createdBy = new Label("Created by code-Redot");
        Hyperlink ghLink = new Hyperlink(GITHUB_URL);
        ghLink.setOnAction(e -> openExternalLink(GITHUB_URL));
        HBox ghRow = new HBox(6, new Label("GitHub:"), ghLink);
        ghRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(8, createdBy, ghRow);
        a.getDialogPane().setContent(content);
        a.showAndWait();
    }

    public Parent getRoot() { return root; }

    // region MainViewContract impl
    @Override public void onStart(Runnable r) { onStart = Objects.requireNonNullElse(r, () -> {}); }
    @Override public void onPause(Runnable r) { onPause = Objects.requireNonNullElse(r, () -> {}); }
    @Override public void onResume(Runnable r) { onResume = Objects.requireNonNullElse(r, () -> {}); }
    @Override public void onCancel(Runnable r) { onCancel = Objects.requireNonNullElse(r, () -> {}); }
    @Override public void onReset(Runnable r) { onReset = Objects.requireNonNullElse(r, () -> {}); }
    @Override public void onAddIgnoreFile(Runnable r) { onAddIgnoreFile = Objects.requireNonNullElse(r, () -> {}); }
    @Override public void onAddIgnoreFolder(Runnable r) { onAddIgnoreFolder = Objects.requireNonNullElse(r, () -> {}); }
    @Override public void onRemoveIgnoreSelected(Consumer<String> c) { onRemoveIgnoreSelected = (c == null) ? s -> {} : c; }

    @Override public String getSourcePath() { return sourceField.getText(); }
    @Override public String getDestinationPath() { return destinationField.getText(); }
    @Override public DestinationMode getDestinationMode() { return destinationModeBox.getValue(); }
    @Override public boolean isDecompressionEnabled() { return decompressCheck.isSelected(); }
    @Override public boolean isSkipShortcutsEnabled() { return skipShortcutsCheck.isSelected(); }
    @Override public int getPartitionItemLimit() {
        Integer v = partitionLimitSpinner.getValue();
        return v == null ? 0 : Math.max(0, v);
    }
    @Override public String getSevenZipPath() { return sevenZipField.getText(); }
    @Override public List<String> getIgnoredPaths() { return List.copyOf(ignoreItems); }
    @Override public String getSelectedIgnoredPath() { return ignoreList.getSelectionModel().getSelectedItem(); }

    @Override
    public void applySettings(CoreSettings settings) {
        if (settings == null) return;
        Platform.runLater(() -> {
            if (settings.lastSourceDirectory() != null) sourceField.setText(settings.lastSourceDirectory());
            if (settings.lastDestinationDirectory() != null) destinationField.setText(settings.lastDestinationDirectory());
            destinationModeBox.getSelectionModel().select(settings.destinationMode());
            decompressCheck.setSelected(settings.decompressionEnabled());
            skipShortcutsCheck.setSelected(settings.skipShortcutsEnabled());
            partitionLimitSpinner.getValueFactory().setValue(Math.max(0, settings.partitionItemLimit()));
            if (settings.sevenZipPath() != null && !settings.sevenZipPath().isBlank()) {
                sevenZipField.setText(settings.sevenZipPath());
            }
            ignoreItems.setAll(settings.ignoredPaths());
        });
    }

    @Override public void setStatusText(String text) {
        Platform.runLater(() -> statusLabel.setText(text == null ? "" : text));
    }
    @Override public void setActiveTaskText(String text) {
        Platform.runLater(() -> activeTaskLabel.setText(text == null ? "" : text));
    }
    @Override public void setProgress(double p) {
        Platform.runLater(() -> progressBar.setProgress(p));
    }
    @Override public void setIgnoredPaths(List<String> paths) {
        Platform.runLater(() -> ignoreItems.setAll(paths == null ? List.of() : paths));
    }
    @Override public void setSevenZipPath(String path) {
        Platform.runLater(() -> sevenZipField.setText(path == null ? "" : path));
    }
    @Override public void showError(String message) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle("Error");
            a.setHeaderText("Operation failed");
            a.setContentText(message == null ? "Unknown error" : message);
            a.show();
        });
    }

    @Override
    public boolean confirmInstallSevenZip(String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Install 7-Zip");
        a.setHeaderText("7-Zip is required to decompress archives");
        a.setContentText(message);
        a.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        return a.showAndWait().filter(b -> b == ButtonType.YES).isPresent();
    }

    @Override
    public String chooseFileToIgnore() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose a file to ignore");
        seedChooserFromSource(fc, null);
        File f = fc.showOpenDialog(stage);
        return f == null ? null : f.getAbsolutePath();
    }

    @Override
    public String chooseFolderToIgnore() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Choose a folder to ignore");
        File f = dc.showDialog(stage);
        return f == null ? null : f.getAbsolutePath();
    }

    private void seedChooserFromSource(FileChooser fc, DirectoryChooser dc) {
        String src = sourceField.getText();
        if (src == null || src.isBlank()) return;
        File d = new File(src.trim());
        if (d.isDirectory()) {
            if (fc != null) fc.setInitialDirectory(d);
            if (dc != null) dc.setInitialDirectory(d);
        }
    }
}
