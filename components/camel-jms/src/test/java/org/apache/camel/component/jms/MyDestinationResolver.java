package org.apache.camel.component.jms;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

import org.springframework.jms.support.destination.DestinationResolver;

public class MyDestinationResolver implements DestinationResolver {

    public Destination resolveDestinationName(Session session, String destinationName, boolean pubSubDomain) throws JMSException {
        if ("logicalNameForTestBQueue".equals(destinationName)) {
            return session.createQueue("test.b");
        } else {
            return session.createQueue(destinationName);
        }
    }

}
