package com.Redot.ui;

import com.Redot.core.destination.DestinationMode;
import javafx.beans.property.*;

public final class MainViewModel {

    // User inputs
    private final StringProperty sourceDir = new SimpleStringProperty("");
    private final StringProperty destinationDir = new SimpleStringProperty("");
    private final BooleanProperty decompressionEnabled = new SimpleBooleanProperty(false);
    private final ObjectProperty<DestinationMode> destinationMode = new SimpleObjectProperty<>(DestinationMode.TRANSFER_DATE);
    private final StringProperty sevenZipPath = new SimpleStringProperty("7z");

    // UI state
    private final StringProperty statusText = new SimpleStringProperty("Ready");
    private final DoubleProperty progress = new SimpleDoubleProperty(0.0); // 0..1
    private final StringProperty activeTaskText = new SimpleStringProperty("");
    private final BooleanProperty running = new SimpleBooleanProperty(false);
    private final BooleanProperty paused = new SimpleBooleanProperty(false);

    // Error reporting (optional alert + status)
    private final StringProperty errorMessage = new SimpleStringProperty("");

    public StringProperty sourceDirProperty() { return sourceDir; }
    public StringProperty destinationDirProperty() { return destinationDir; }
    public BooleanProperty decompressionEnabledProperty() { return decompressionEnabled; }
    public ObjectProperty<DestinationMode> destinationModeProperty() { return destinationMode; }
    public StringProperty sevenZipPathProperty() { return sevenZipPath; }

    public StringProperty statusTextProperty() { return statusText; }
    public DoubleProperty progressProperty() { return progress; }
    public StringProperty activeTaskTextProperty() { return activeTaskText; }
    public BooleanProperty runningProperty() { return running; }
    public BooleanProperty pausedProperty() { return paused; }

    public StringProperty errorMessageProperty() { return errorMessage; }

    public void setStatus(String s) { statusText.set(s); }
    public void setError(String s) { errorMessage.set(s == null ? "" : s); }
}
