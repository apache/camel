package org.apache.camel.component.sjms.support;

import org.apache.activemq.ActiveMQMessageConsumer;
import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ConsumerId;
import org.apache.activemq.command.MessageDispatch;

import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

/**
 * Created by bryan.love on 3/22/17.
 */
public class MockMessageConsumer extends ActiveMQMessageConsumer{
    private boolean isBadSession;

    public MockMessageConsumer(ActiveMQSession session, ConsumerId consumerId, ActiveMQDestination dest, String name, String selector, int prefetch, int maximumPendingMessageCount, boolean noLocal, boolean browser, boolean dispatchAsync, MessageListener messageListener, boolean isBadSession) throws JMSException {
        super(session, consumerId, dest, name, selector, prefetch, maximumPendingMessageCount, noLocal, browser, dispatchAsync, messageListener);
        this.isBadSession = isBadSession;
    }

    public Message receive(long timeout) throws JMSException {
        if(isBadSession) throw new IllegalStateException("asdf");
        return super.receive(timeout);
    }
}
