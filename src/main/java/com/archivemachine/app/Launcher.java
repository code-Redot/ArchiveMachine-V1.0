/*
 * Indirect entry point: when jpackage wraps a class that directly extends
 * javafx.application.Application, the JavaFX runtime requires its modules
 * on the module-path. Using a non-Application Launcher class side-steps that
 * check and lets us ship JavaFX as classpath jars.
 */
package com.archivemachine.app;

public final class Launcher {
    public static void main(String[] args) {
        Main.main(args);
    }
}
