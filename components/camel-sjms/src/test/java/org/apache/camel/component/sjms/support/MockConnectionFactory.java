package org.apache.camel.component.sjms.support;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.management.JMSStatsImpl;
import org.apache.activemq.transport.Transport;

import javax.jms.Connection;
import javax.jms.JMSException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by bryan.love on 3/22/17.
 */
public class MockConnectionFactory extends ActiveMQConnectionFactory {
    private int returnBadSessionNTimes = 0;

    public Connection createConnection() throws JMSException {
        return this.createActiveMQConnection();
    }
    public MockConnectionFactory(String brokerURL) {
        super(createURI(brokerURL));
    }
    private static URI createURI(String brokerURL) {
        try {
            return new URI(brokerURL);
        } catch (URISyntaxException var2) {
            throw (IllegalArgumentException)(new IllegalArgumentException("Invalid broker URI: " + brokerURL)).initCause(var2);
        }
    }

    protected ActiveMQConnection createActiveMQConnection(Transport transport, JMSStatsImpl stats) throws Exception {
        MockConnection connection = new MockConnection(transport, this.getClientIdGenerator(), this.getConnectionIdGenerator(), stats, returnBadSessionNTimes);
        return connection;
    }

    public void returnBadSessionNTimes(int returnBadSessionNTimes) {
        this.returnBadSessionNTimes = returnBadSessionNTimes;
    }

}
