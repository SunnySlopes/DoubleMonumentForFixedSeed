package project;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Application entry point.
 */
public class Launcher {

    private static final Logger LOGGER = Logger.getLogger(Launcher.class.getName());

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "设置系统外观失败", e);
            }
            new DoubleMonumentForFixedSeed().setVisible(true);
        });
    }
}
