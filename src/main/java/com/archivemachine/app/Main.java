/*
 * JavaFX entry point.
 * Composition root: builds the core services and wires Presenter <-> View.
 */
package com.archivemachine.app;

import com.archivemachine.core.pipeline.PipelinePlanner;
import com.archivemachine.core.settings.SettingsStore;
import com.archivemachine.core.task.TaskManager;
import com.archivemachine.io.sevenzip.SevenZipInstaller;
import com.archivemachine.io.sevenzip.SevenZipLocator;
import com.archivemachine.mvp.MainPresenter;
import com.archivemachine.mvp.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        SettingsStore settingsStore = new SettingsStore();
        TaskManager taskManager = new TaskManager();
        SevenZipLocator locator = new SevenZipLocator();
        SevenZipInstaller installer = new SevenZipInstaller();
        PipelinePlanner planner = new PipelinePlanner();

        MainPresenter presenter = new MainPresenter(taskManager, settingsStore, locator, installer, planner);
        MainView view = new MainView(stage);
        presenter.attachView(view);

        stage.setTitle("ArchiveMachine 1.1");
        stage.setScene(new Scene(view.getRoot()));
        stage.sizeToScene();
        stage.setResizable(false);
        stage.show();

        presenter.onStartup();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
