package github.nighter.smartspawner.spawner.gui.synchronization.utils;

/**
 * Utility class for formatting time values in spawner GUIs.
 * Provides consistent time display formatting across the plugin.
 */
public final class TimerFormatter {

    private TimerFormatter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Formats milliseconds into a readable time string (mm:ss format).
     *
     * @param milliseconds The time in milliseconds
     * @return Formatted time string (e.g., "01:30", "00:45")
     */
    public static String formatTime(long milliseconds) {
        if (milliseconds <= 0) {
            return "00:00";
        }
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
