/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.jms;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.camel.CamelContainer;
import org.apache.camel.Component;
import org.apache.camel.Processor;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.SessionCallback;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

import com.sun.jndi.toolkit.url.Uri;

/**
 * @version $Revision$
 */
public class JmsComponent implements Component<JmsExchange, JmsEndpoint> {
    private JmsTemplate template = new JmsTemplate();
    private static final String QUEUE_PREFIX = "queue/";
    private static final String TOPIC_PREFIX = "topic/";
    private CamelContainer container;

    public JmsComponent(CamelContainer container) {
        this.container = container;
    }

    public JmsEndpoint createEndpoint(Uri uri) {
        // lets figure out from the URI whether its a queue, topic etc

        String path = uri.getPath();
        return createEndpoint(uri.toString(), path);
    }

    public JmsEndpoint createEndpoint(String uri, String path) {
        if (path.startsWith(QUEUE_PREFIX)) {
            template.setPubSubDomain(false);
            path = path.substring(QUEUE_PREFIX.length());
        }
        else if (path.startsWith(TOPIC_PREFIX)) {
            template.setPubSubDomain(false);
            path = path.substring(TOPIC_PREFIX.length());
        }

        final String subject = convertPathToActualDestination(path);
        template.setDefaultDestinationName(subject);

        Destination destination = (Destination) template.execute(new SessionCallback() {
            public Object doInJms(Session session) throws JMSException {
                return template.getDestinationResolver().resolveDestinationName(session, subject, template.isPubSubDomain());
            }
        });

        AbstractMessageListenerContainer listenerContainer = createMessageListenerContainer(template);
        return new JmsEndpoint(uri, container, destination, template ,listenerContainer);
    }

    public JmsTemplate getTemplate() {
        return template;
    }

    public void setTemplate(JmsTemplate template) {
        this.template = template;
    }

    protected AbstractMessageListenerContainer createMessageListenerContainer(JmsTemplate template) {
        // TODO use an enum to auto-switch container types?
        return new DefaultMessageListenerContainer();
    }

    /**
     * A strategy method allowing the URI destination to be translated into the actual JMS destination name
     * (say by looking up in JNDI or something)
     */
    protected String convertPathToActualDestination(String path) {
        return path;
    }

	public void activate(JmsEndpoint endpoint, Processor<JmsExchange> processor) {
		// TODO Auto-generated method stub
	}
	public void deactivate(JmsEndpoint endpoint) {
		// TODO Auto-generated method stub
	}
}
