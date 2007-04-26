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
package org.apache.camel.component.jms;

import static org.apache.camel.util.ObjectHelper.removeStartingCharacters;

import java.util.Map;

import javax.jms.ConnectionFactory;
import javax.jms.Session;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.IntrospectionSupport;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * @version $Revision:520964 $
 */
public class JmsComponent extends DefaultComponent<JmsExchange> {
    public static final String QUEUE_PREFIX = "queue:";
    public static final String TOPIC_PREFIX = "topic:";
    private JmsConfiguration configuration;

    /**
     * Static builder method
     */
    public static JmsComponent jmsComponent() {
        return new JmsComponent();
    }

    /**
     * Static builder method
     */
    public static JmsComponent jmsComponent(JmsConfiguration configuration) {
        return new JmsComponent(configuration);
    }

    /**
     * Static builder method
     */
    public static JmsComponent jmsComponent(ConnectionFactory connectionFactory) {
        return jmsComponent(new JmsConfiguration(connectionFactory));
    }

    /**
     * Static builder method
     */
    public static JmsComponent jmsComponentClientAcknowledge(ConnectionFactory connectionFactory) {
        JmsConfiguration template = new JmsConfiguration(connectionFactory);
        template.setAcknowledgementMode(Session.AUTO_ACKNOWLEDGE);
        return jmsComponent(template);
    }

    public static JmsComponent jmsComponentTransacted(ConnectionFactory connectionFactory) {
        JmsConfiguration template = new JmsConfiguration(connectionFactory);
        template.setTransacted(true);
        return jmsComponent(template);
    }

	public static JmsComponent jmsComponentTransacted(ConnectionFactory connectionFactory, PlatformTransactionManager transactionManager) {
        JmsConfiguration template = new JmsConfiguration(connectionFactory);
        template.setTransactionManager(transactionManager);
        template.setTransacted(true);
        return jmsComponent(template);
	}

    public JmsComponent() {
        this.configuration = new JmsConfiguration();
    }

    public JmsComponent(JmsConfiguration configuration) {
        this.configuration = configuration;
    }

    public JmsComponent(CamelContext context) {
        super(context);
        this.configuration = new JmsConfiguration();
    }

    @Override
    protected Endpoint<JmsExchange> createEndpoint(String uri, String remaining, Map parameters) throws Exception {

        boolean pubSubDomain = false;
        if (remaining.startsWith(QUEUE_PREFIX)) {
            pubSubDomain = false;
            remaining = removeStartingCharacters(remaining.substring(QUEUE_PREFIX.length()), '/');
        }
        else if (remaining.startsWith(TOPIC_PREFIX)) {
            pubSubDomain = true;
            remaining = removeStartingCharacters(remaining.substring(TOPIC_PREFIX.length()), '/');
        }

        final String subject = convertPathToActualDestination(remaining);

        // lets make sure we copy the configuration as each endpoint can customize its own version
        JmsEndpoint endpoint = new JmsEndpoint(uri, this, subject, pubSubDomain, getConfiguration().copy());

        String selector = (String) parameters.remove("selector");
        if (selector != null) {
            endpoint.setSelector(selector);
        }
        IntrospectionSupport.setProperties(endpoint.getConfiguration(), parameters);
        return endpoint;
    }

    public JmsConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the JMS configuration
     *
     * @param configuration the configuration to use by default for endpoints
     */
    public void setConfiguration(JmsConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * A strategy method allowing the URI destination to be translated into the actual JMS destination name
     * (say by looking up in JNDI or something)
     */
    protected String convertPathToActualDestination(String path) {
        return path;
    }
}
