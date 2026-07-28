package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.TimeUnit;

public class SpeedrunTimer {
    private static boolean timerRunning = false;
    private static long startTime = 0;
    private static long elapsedNanos = 0;
    private static long finalElapsedNanos = 0;
    private static boolean objectivesCompleted = false;

    private static boolean manualPaused = false;
    private static boolean systemPaused = false;
    private static long pauseStartTime = 0;
    private static long totalPauseNanos = 0;

    // HUD: 0=COMPLET, 1=MINIMAL, 2=MASQUÉ
    private static int hudState = 0;

    // Manual stats tracking
    private static Vec3 lastPos = null;
    private static boolean isStatsInitialized = false;
    private static boolean wasDead = false;
    private static int deathCount = 0;
    private static double traveledMeters = 0;
    private static long daysPlayed = 0;

    public static void reset() {
        timerRunning = false;
        startTime = 0;
        elapsedNanos = 0;
        totalPauseNanos = 0;
        manualPaused = false;
        systemPaused = false;
        objectivesCompleted = false;
        lastPos = null;
        isStatsInitialized = false;
        wasDead = false;
        deathCount = 0;
        traveledMeters = 0;
        daysPlayed = 0;
    }

    public static void start() {
        if (!timerRunning) {
            timerRunning = true;
            startTime = System.nanoTime();
            totalPauseNanos = 0;
            deathCount = 0;
            traveledMeters = 0;
            objectivesCompleted = false;
        }
    }

    public static void stop() {
        if (timerRunning) {
            timerRunning = false;
            elapsedNanos = System.nanoTime() - startTime - totalPauseNanos;
        }
    }

    public static void markCompleted() {
        objectivesCompleted = true;
        finalElapsedNanos = computeEffectiveNanos();
        stop();
    }

    public static void toggleHud() {
        hudState = (hudState + 1) % 3;
    }

    public static void toggleManualPause() {
        if (!timerRunning && !objectivesCompleted) return;

        manualPaused = !manualPaused;
        if (manualPaused) {
            if (!systemPaused) {
                pauseStartTime = System.nanoTime();
            }
        } else {
            if (!systemPaused) {
                totalPauseNanos += (System.nanoTime() - pauseStartTime);
            }
        }
    }

    public static void onSystemPause(boolean paused) {
        if (!timerRunning || manualPaused) return;

        if (paused) {
            if (!systemPaused) {
                systemPaused = true;
                pauseStartTime = System.nanoTime();
            }
        } else {
            if (systemPaused) {
                systemPaused = false;
                totalPauseNanos += (System.nanoTime() - pauseStartTime);
            }
        }
    }

    public static void trackPlayerStats() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!isStatsInitialized || lastPos == null) {
            lastPos = mc.player.position();
            isStatsInitialized = true;
            wasDead = mc.player.isDeadOrDying();
        }

        Vec3 currentPos = mc.player.position();
        traveledMeters += currentPos.distanceTo(lastPos);
        lastPos = currentPos;

        boolean isDead = mc.player.isDeadOrDying();
        if (isDead && !wasDead) {
            deathCount++;
        }
        wasDead = isDead;

        daysPlayed = mc.player.level().getDayTime() / 24000L;
    }

    public static long computeEffectiveNanos() {
        if (!timerRunning) return elapsedNanos;
        if (manualPaused || systemPaused) return pauseStartTime - startTime - totalPauseNanos;
        return System.nanoTime() - startTime - totalPauseNanos;
    }

    public static String getFormattedTimeFromNanos(long nanos) {
        long millis = TimeUnit.NANOSECONDS.toMillis(nanos);
        long h = TimeUnit.MILLISECONDS.toHours(millis);
        long m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long s = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        long ms = millis % 1000;
        if (h > 0) return String.format("%02d:%02d:%02d.%03d", h, m, s, ms);
        return String.format("%02d:%02d.%03d", m, s, ms);
    }

    public static String currentFormattedTime() {
        return getFormattedTimeFromNanos(computeEffectiveNanos());
    }

    // Getters
    public static boolean isRunning() { return timerRunning; }
    public static boolean isObjectivesCompleted() { return objectivesCompleted; }
    public static long getFinalElapsedNanos() { return finalElapsedNanos; }
    public static int getHudState() { return hudState; }
    public static boolean isManualPaused() { return manualPaused; }
    public static boolean isSystemPaused() { return systemPaused; }
    public static boolean isPaused() { return manualPaused || systemPaused; }
    public static int getDeathCount() { return deathCount; }
    public static double getTraveledMeters() { return traveledMeters; }
    public static long getDaysPlayed() { return daysPlayed; }

    // Package-private setters for SpeedrunState coordination
    static void setObjectivesCompleted(boolean v) { objectivesCompleted = v; }
}
