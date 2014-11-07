package org.apache.camel.component.sjms.jms;

import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.Test;

import javax.jms.Queue;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.Topic;

/**
 * @author
 */
public class DefaultDestinationCreationStrategyTest extends JmsTestSupport {

    private DestinationCreationStrategy strategy = new DefaultDestinationCreationStrategy();

    @Test
    public void testQueueCreation() throws Exception {
        Queue destination = (Queue)strategy.createDestination(getSession(), "queue://test", false);
        assertNotNull(destination);
        assertEquals("test", destination.getQueueName());

        destination = (Queue)strategy.createDestination(getSession(), "test", false);
        assertNotNull(destination);
        assertEquals("test", destination.getQueueName());
    }

    @Test
    public void testTopicCreation() throws Exception {
        Topic destination = (Topic)strategy.createDestination(getSession(), "topic://test", true);
        assertNotNull(destination);
        assertEquals("test", destination.getTopicName());

        destination = (Topic)strategy.createDestination(getSession(), "test", true);
        assertNotNull(destination);
        assertEquals("test", destination.getTopicName());
    }

    @Test
    public void testTemporaryQueueCreation() throws Exception {
        TemporaryQueue destination = (TemporaryQueue)strategy.createTemporaryDestination(getSession(), false);
        assertNotNull(destination);
        assertNotNull(destination.getQueueName());
    }

    @Test
    public void testTemporaryTopicCreation() throws Exception {
        TemporaryTopic destination = (TemporaryTopic)strategy.createTemporaryDestination(getSession(), true);
        assertNotNull(destination);
        assertNotNull(destination.getTopicName());
    }
}