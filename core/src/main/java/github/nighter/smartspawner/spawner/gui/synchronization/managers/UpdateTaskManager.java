package github.nighter.smartspawner.spawner.gui.synchronization.managers;

import github.nighter.smartspawner.Scheduler;

/**
 * Manages the lifecycle of the scheduled update task.
 * Handles starting and stopping the periodic GUI update task in a thread-safe manner.
 */
public class UpdateTaskManager {

    private static final long UPDATE_INTERVAL_TICKS = 20L; // 1 second updates
    private static final long INITIAL_DELAY_TICKS = 20L;   // Match the update interval

    private Scheduler.Task updateTask;
    private volatile boolean isTaskRunning;

    /**
     * Starts the update task if not already running.
     *
     * @param updateRunnable The runnable to execute periodically
     */
    public synchronized void startTask(Runnable updateRunnable) {
        if (isTaskRunning) {
            return;
        }

        updateTask = Scheduler.runTaskTimer(updateRunnable, INITIAL_DELAY_TICKS, UPDATE_INTERVAL_TICKS);
        isTaskRunning = true;
    }

    /**
     * Stops the update task if running.
     */
    public synchronized void stopTask() {
        if (!isTaskRunning) {
            return;
        }

        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        isTaskRunning = false;
    }

    /**
     * Checks if the update task is currently running.
     *
     * @return true if task is running
     */
    public boolean isRunning() {
        return isTaskRunning;
    }
}
