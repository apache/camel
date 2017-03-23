package org.apache.camel.component.sjms.support;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.management.JMSStatsImpl;
import org.apache.activemq.transport.Transport;
import org.apache.activemq.util.IdGenerator;

import javax.jms.JMSException;
import javax.jms.Session;

/**
 * Created by bryan.love on 3/22/17.
 */
public class MockConnection extends ActiveMQConnection {
    private int returnBadSessionNTimes = 0;

    protected MockConnection(final Transport transport, IdGenerator clientIdGenerator, IdGenerator connectionIdGenerator, JMSStatsImpl factoryStats, int returnBadSessionNTimes) throws Exception {
        super(transport,  clientIdGenerator,  connectionIdGenerator,  factoryStats);
        this.returnBadSessionNTimes = returnBadSessionNTimes;
    }

    public Session createSession(boolean transacted, int acknowledgeMode) throws JMSException {
        this.checkClosedOrFailed();
        this.ensureConnectionInfoSent();
        if(!transacted) {
            if(acknowledgeMode == 0) {
                throw new JMSException("acknowledgeMode SESSION_TRANSACTED cannot be used for an non-transacted Session");
            }

            if(acknowledgeMode < 0 || acknowledgeMode > 4) {
                throw new JMSException("invalid acknowledgeMode: " + acknowledgeMode + ". Valid values are Session.AUTO_ACKNOWLEDGE (1), Session.CLIENT_ACKNOWLEDGE (2), Session.DUPS_OK_ACKNOWLEDGE (3), ActiveMQSession.INDIVIDUAL_ACKNOWLEDGE (4) or for transacted sessions Session.SESSION_TRANSACTED (0)");
            }
        }

        boolean useBadSession = false;
        if(returnBadSessionNTimes > 0){
            useBadSession = true;
            returnBadSessionNTimes = returnBadSessionNTimes - 1;
        }
        return new MockSession(this, this.getNextSessionId(), transacted?0:acknowledgeMode, this.isDispatchAsync(), this.isAlwaysSessionAsync(), useBadSession);

    }
}
