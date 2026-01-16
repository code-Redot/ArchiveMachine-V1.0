/*
 * This project is coded and maintained by Code-ReDot, coderedot@outlook.com.
 * Purpose: JavaFX entry point for ArchiveMachine.
 * Wires View + Presenter + TaskManager, then shows the primary stage.
 */
package com.Redot;

import com.Redot.core.settings.SettingsStore;
import com.Redot.core.task.TaskManager;
import com.Redot.ui.MainPresenter;
import com.Redot.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        // Settings store creation
        SettingsStore settingsStore = new SettingsStore();

        // Core task manager creation
        TaskManager taskManager = new TaskManager();

        // Presenter ↔ view wiring
        MainPresenter presenter = new MainPresenter(taskManager, settingsStore);
        MainView view = new MainView(stage);
        presenter.attachView(view);
        presenter.onStartup();

        // Stage setup (scene, title, resizable, show)
        stage.setTitle("ArchiveMachine");
        stage.setScene(new Scene(view.getRoot()));
        stage.sizeToScene();
        stage.setResizable(false);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
