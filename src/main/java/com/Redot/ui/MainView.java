/*
 * Purpose: Builds the JavaFX UI for the main window (View in MVP).
 * Owns controls and dialogs; delegates all actions via MainViewContract callbacks.
 */
package com.Redot.ui;

import com.Redot.core.destination.DestinationMode;
import com.Redot.core.settings.CoreSettings;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.Objects;

public final class MainView implements MainViewContract {

    private static final String GITHUB_URL = "https://github.com/code-Redot";

    private final Parent root;

    // region UI inputs (source/destination/mode)
    private final TextField sourceField = new TextField();
    private final TextField destinationField = new TextField();
    private final ComboBox<DestinationMode> destinationModeBox = new ComboBox<>();

    // region Feature toggles (decompress, skip shortcuts, partitioning)
    private final CheckBox decompressCheck = new CheckBox("Decompress archives (.rar/.7z/.zip)");
    private final CheckBox skipShortcutsCheck = new CheckBox("Skip shortcuts (.lnk / symlinks)");

    private final Spinner<Integer> partitionLimitSpinner = new Spinner<>(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Integer.MAX_VALUE, 0)
    );
    private final TextField sevenZipField = new TextField("7z");

    // region Progress + status output
    private final Label statusLabel = new Label("Ready");
    private final Label activeTaskLabel = new Label("");
    private final ProgressBar progressBar = new ProgressBar(0.0);

    // region Action buttons (start/pause/resume/cancel/reset)
    private final Button startBtn = new Button("Start");
    private final Button pauseBtn = new Button("Pause");
    private final Button resumeBtn = new Button("Resume");
    private final Button cancelBtn = new Button("Cancel");
    private final Button resetBtn = new Button("Reset");

    // Presenter callbacks (wired by MainPresenter)
    private Runnable onStart = () -> {};
    private Runnable onPause = () -> {};
    private Runnable onResume = () -> {};
    private Runnable onCancel = () -> {};
    private Runnable onReset = () -> {};

    public MainView(Stage stage) {
        // Source/Destination browse buttons
        Button srcBtn = new Button("Select...");
        Button dstBtn = new Button("Select...");

        // About/info button
        Button infoBtn = new Button("ℹ");
        infoBtn.setFocusTraversable(false);
        infoBtn.setTooltip(new Tooltip("About"));
        infoBtn.setOnAction(e -> showAboutDialog());

        // Directory pickers
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

        // Destination mode
        destinationModeBox.getItems().setAll(DestinationMode.values());
        destinationModeBox.getSelectionModel().select(DestinationMode.TRANSFER_DATE);

        // Event wiring for control buttons
        startBtn.setOnAction(e -> onStart.run());
        pauseBtn.setOnAction(e -> onPause.run());
        resumeBtn.setOnAction(e -> onResume.run());
        cancelBtn.setOnAction(e -> onCancel.run());
        resetBtn.setOnAction(e -> onReset.run());

        // Progress output
        progressBar.setMaxWidth(Double.MAX_VALUE);

        // Inputs wiring/validation
        sevenZipField.disableProperty().bind(decompressCheck.selectedProperty().not());
        partitionLimitSpinner.setEditable(true);
        partitionLimitSpinner.setPrefWidth(140);

        // region Layout (GridPane rows + constraints)
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));
        grid.setMaxWidth(Region.USE_PREF_SIZE);

        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(170);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setMinWidth(Region.USE_PREF_SIZE);
        grid.getColumnConstraints().addAll(c0, c1, c2);

        // Grid: top info button row (standalone row above Source)
        grid.add(infoBtn, 2, 0);
        GridPane.setHalignment(infoBtn, javafx.geometry.HPos.RIGHT);
        GridPane.setValignment(infoBtn, javafx.geometry.VPos.TOP);
        GridPane.setMargin(infoBtn, new Insets(0, 0, 6, 0));

        // Grid: source row
        grid.add(new Label("Source Directory:"), 0, 1);
        grid.add(sourceField, 1, 1);
        grid.add(srcBtn, 2, 1);

        // Grid: destination row
        grid.add(new Label("Destination Directory:"), 0, 2);
        grid.add(destinationField, 1, 2);
        grid.add(dstBtn, 2, 2);

        // Grid: mode + options
        grid.add(new Label("Destination Mode:"), 0, 3);
        grid.add(destinationModeBox, 1, 3);
        grid.add(decompressCheck, 1, 4);
        grid.add(skipShortcutsCheck, 1, 5);

        // Grid: partitioning
        grid.add(new Label("Partition item limit:"), 0, 6);
        grid.add(partitionLimitSpinner, 1, 6);

        // Grid: 7-zip path
        grid.add(new Label("7-Zip Executable:"), 0, 7);
        grid.add(sevenZipField, 1, 7);

        // Control buttons row (left-aligned)
        HBox buttonsBox = new HBox(10, startBtn, pauseBtn, resumeBtn, cancelBtn, resetBtn);
        buttonsBox.setAlignment(Pos.CENTER_LEFT);

        // Status/progress outputs (aligned to the same grid origin)
        Label currentTaskTitle = new Label("Current task:");
        Label statusTitle = new Label("Status:");

        int row = 8;
        grid.add(buttonsBox, 0, row++, 3, 1);
        grid.add(progressBar, 0, row++, 3, 1);
        grid.add(currentTaskTitle, 0, row++, 3, 1);
        grid.add(activeTaskLabel, 0, row++, 3, 1);
        grid.add(statusTitle, 0, row++, 3, 1);
        grid.add(statusLabel, 0, row, 3, 1);

        GridPane.setFillWidth(progressBar, true);

        // Root: center the grid block while preserving internal left alignment
        StackPane rootPane = new StackPane(grid);
        StackPane.setAlignment(grid, Pos.TOP_CENTER);
        rootPane.setPadding(new Insets(12));

        this.root = rootPane;
    }

    // region About dialog + external link
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
        a.setHeaderText("ArchiveMachine");

        Label createdBy = new Label("Created by code-Redot");
        Label githubLabel = new Label("GitHub:");
        Hyperlink githubLink = new Hyperlink(GITHUB_URL);
        githubLink.setOnAction(e -> openExternalLink(GITHUB_URL));

        HBox ghRow = new HBox(6, githubLabel, githubLink);
        ghRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(8, createdBy, ghRow);
        a.getDialogPane().setContent(content);
        a.showAndWait();
    }

    public Parent getRoot() { return root; }

    // region MainViewContract implementation

    // Contract: event registration
    @Override public void onStart(Runnable r) { onStart = Objects.requireNonNullElse(r, () -> {}); }
    @Override public void onPause(Runnable r) { onPause = Objects.requireNonNullElse(r, () -> {}); }
    @Override public void onResume(Runnable r) { onResume = Objects.requireNonNullElse(r, () -> {}); }
    @Override public void onCancel(Runnable r) { onCancel = Objects.requireNonNullElse(r, () -> {}); }
    @Override public void onReset(Runnable r) { onReset = Objects.requireNonNullElse(r, () -> {}); }

        // Contract: input getters
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

    
    // Contract: apply persisted settings
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
        });
    }

    
    // Contract: status/progress outputs
    @Override public void setStatusText(String text) {
        Platform.runLater(() -> statusLabel.setText(text == null ? "" : text));
    }

    @Override public void setActiveTaskText(String text) {
        Platform.runLater(() -> activeTaskLabel.setText(text == null ? "" : text));
    }

    @Override public void setProgress(double progress0to1) {
        Platform.runLater(() -> progressBar.setProgress(progress0to1));
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
}
