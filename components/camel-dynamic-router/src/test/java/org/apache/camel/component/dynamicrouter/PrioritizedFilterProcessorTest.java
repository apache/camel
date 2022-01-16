package org.apache.camel.component.dynamicrouter;

import org.apache.camel.component.dynamicrouter.support.DynamicRouterTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PrioritizedFilterProcessorTest extends DynamicRouterTestSupport {

    @Test
    void testCompareToAndEqual() {
        PrioritizedFilterProcessor testProcessor
                = new PrioritizedFilterProcessor(TEST_ID, TEST_PRIORITY, context, predicate, processor);
        assertEquals(0, testProcessor.compareTo(prioritizedFilterProcessor));
    }

    @Test
    void testCompareToAndNotEqualById() {
        PrioritizedFilterProcessor testProcessor
                = new PrioritizedFilterProcessor("differentId", TEST_PRIORITY, context, predicate, processor);
        assertNotEquals(0, testProcessor.compareTo(prioritizedFilterProcessor));
    }

    @Test
    void testCompareToAndNotEqualByPriority() {
        PrioritizedFilterProcessor testProcessor = new PrioritizedFilterProcessor(TEST_ID, 1, context, predicate, processor);
        assertNotEquals(0, testProcessor.compareTo(prioritizedFilterProcessor));
    }

    @Test
    void testToString() {
        PrioritizedFilterProcessor testProcessor
                = new PrioritizedFilterProcessor(TEST_ID, TEST_PRIORITY, context, predicate, processor);
        String expected = String.format("PrioritizedFilterProcessor [id: %s, priority: %s, predicate: %s]",
                TEST_ID, TEST_PRIORITY, TEST_PREDICATE);
        String result = testProcessor.toString();
        assertEquals(expected, result);
    }
}
