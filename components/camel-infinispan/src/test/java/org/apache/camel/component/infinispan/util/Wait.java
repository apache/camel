package org.apache.camel.component.infinispan.util;

import static org.junit.Assert.assertTrue;

/**
 * @author Martin Gencur
 */
public class Wait {

    public static void waitFor(Condition ec, long timeout) {
        waitFor(ec, timeout, 10);
    }

    /**
     * @param ec Condition that has to be met after the timeout
     * @param timeout Overall timeout - how long to wait for the condition
     * @param loops How many times to check the condition before the timeout expires.
     */
    public static void waitFor(Condition ec, long timeout, int loops) {
        if (loops <= 0) {
            throw new IllegalArgumentException("Number of loops must be positive");
        }
        long sleepDuration = timeout / loops;
        if (sleepDuration == 0) {
            sleepDuration = 1;
        }
        try {
            for (int i = 0; i < loops; i++) {
                if (ec.isSatisfied()) {
                    return;
                }
                Thread.sleep(sleepDuration);
            }
            assertTrue("The condition was not satisfied after " + timeout + " ms", ec.isSatisfied());
        } catch (Exception e) {
            throw new RuntimeException("Unexpected!", e);
        }
    }
}