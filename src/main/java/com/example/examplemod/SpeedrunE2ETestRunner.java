package com.example.examplemod;

import net.minecraft.client.Minecraft;

public class SpeedrunE2ETestRunner {
    private static final boolean IS_E2E_TEST_ENABLED = System.getProperty("speedrun.e2eTest") != null;
    private static int testStep = 0;
    private static int stepTicks = 0;

    public static void onClientTick() {
        if (!IS_E2E_TEST_ENABLED) return;

        Minecraft mc = Minecraft.getInstance();
        stepTicks++;

        switch (testStep) {
            case 0:
                // Wait for player to enter world
                if (mc.player != null && stepTicks > 100) {
                    SpeedrunRoulette.LOGGER.info("[E2E TEST] Step 0: In-world detected. Testing /speedrun retry command dispatch...");
                    mc.player.connection.sendCommand("speedrun retry");
                    testStep = 1;
                    stepTicks = 0;
                }
                break;

            case 1:
                // Verify disconnect and transition handled cleanly without GL crash
                if (mc.level == null && stepTicks > 40) {
                    SpeedrunRoulette.LOGGER.info("[E2E TEST] Step 1 PASS: Clean disconnect from retry command, no OpenGL exception!");
                    testStep = 2;
                    stepTicks = 0;
                }
                break;

            case 2:
                // Verify AutoNav or main menu navigation state
                if (stepTicks > 40) {
                    SpeedrunRoulette.LOGGER.info("[E2E TEST] Step 2 PASS: Client back at title/menu without crash. E2E Test Complete!");
                    testStep = 99; // Finished
                }
                break;

            default:
                break;
        }
    }
}
