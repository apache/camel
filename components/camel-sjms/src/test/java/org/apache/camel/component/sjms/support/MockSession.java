package org.apache.camel.component.sjms.support;

import org.apache.activemq.*;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTempQueue;
import org.apache.activemq.command.SessionId;

import javax.jms.*;

/**
 * Created by bryan.love on 3/22/17.
 */
public class MockSession extends ActiveMQSession {
    private boolean isBadSession = false;

    protected MockSession(ActiveMQConnection connection, SessionId sessionId, int acknowledgeMode, boolean asyncDispatch, boolean sessionAsyncDispatch, boolean isBadSession) throws JMSException {
        super(connection,  sessionId,  acknowledgeMode,  asyncDispatch,  sessionAsyncDispatch);
        this.isBadSession = isBadSession;
    }
    public Queue createQueue(String queueName) throws JMSException {
        this.checkClosed();
        return (Queue)(queueName.startsWith("ID:")?new ActiveMQTempQueue(queueName):new ActiveMQQueue(queueName));
    }

    public MessageConsumer createConsumer(Destination destination, String messageSelector, boolean noLocal, MessageListener messageListener) throws JMSException {
        this.checkClosed();
        if(destination instanceof CustomDestination) {
            CustomDestination prefetchPolicy1 = (CustomDestination)destination;
            return prefetchPolicy1.createConsumer(this, messageSelector, noLocal);
        } else {
            ActiveMQPrefetchPolicy prefetchPolicy = this.connection.getPrefetchPolicy();
            boolean prefetch = false;
            int prefetch1;
            if(destination instanceof Topic) {
                prefetch1 = prefetchPolicy.getTopicPrefetch();
            } else {
                prefetch1 = prefetchPolicy.getQueuePrefetch();
            }

            ActiveMQDestination activemqDestination = ActiveMQMessageTransformation.transformDestination(destination);
            return new MockMessageConsumer(this, this.getNextConsumerId(), activemqDestination, (String)null, messageSelector, prefetch1, prefetchPolicy.getMaximumPendingMessageLimit(), noLocal, false, this.isAsyncDispatch(), messageListener, isBadSession);
        }
    }
}
