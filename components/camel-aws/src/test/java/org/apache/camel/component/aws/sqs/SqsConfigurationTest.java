package org.apache.camel.component.aws.sqs;

import static org.junit.Assert.*;

import org.junit.Test;

public class SqsConfigurationTest {

    @Test
    public void itReturnsAnInformativeErrorForBadMessageGroupIdStrategy() throws Exception {
        SqsConfiguration sqsConfiguration = new SqsConfiguration();
        try {
            sqsConfiguration.setMessageGroupIdStrategy("useUnknownStrategy");
            fail("Should have thrown exception");
        } catch (Exception e) {
            assertTrue("Bad error message: " + e.getMessage(), e.getMessage().startsWith("Unrecognised MessageGroupIdStrategy"));
        }
    }

    
    @Test
    public void itReturnsAnInformativeErrorForBadMessageDeduplicationIdStrategy() throws Exception {
        SqsConfiguration sqsConfiguration = new SqsConfiguration();
        try {
            sqsConfiguration.setMessageDeduplicationIdStrategy("useUnknownStrategy");
            fail("Should have thrown exception");
        } catch (Exception e) {
            assertTrue("Bad error message: " + e.getMessage(), e.getMessage().startsWith("Unrecognised MessageDeduplicationIdStrategy"));
        }
    }

}
