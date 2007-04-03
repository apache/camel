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

import com.sun.jndi.toolkit.url.Uri;
import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

import javax.jms.ConnectionFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * @version $Revision:520964 $
 */
public class JmsComponent extends DefaultComponent<JmsExchange> {
    public static final String QUEUE_PREFIX = "queue/";
    public static final String TOPIC_PREFIX = "topic/";
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
        template.setProducerAcknowledgementMode("CLIENT_ACKNOWLEDGE");
        template.setConsumerAcknowledgementMode("CLIENT_ACKNOWLEDGE");
        return jmsComponent(template);
    }

    protected JmsComponent() {
        this.configuration = new JmsConfiguration();
    }

    public JmsComponent(JmsConfiguration configuration) {
        this.configuration = configuration;
    }

    public JmsComponent(CamelContext context) {
        super(context);
        this.configuration = new JmsConfiguration();
    }

    public JmsEndpoint createEndpoint(Uri uri) throws URISyntaxException {
        // lets figure out from the URI whether its a queue, topic etc

        String path = uri.getPath();
        return createEndpoint(uri.toString(), path);
    }

    public JmsEndpoint createEndpoint(String uri, String path) throws URISyntaxException {
        ObjectHelper.notNull(getCamelContext(), "camelContext");

        boolean pubSubDomain = false;
        if (path.startsWith(QUEUE_PREFIX)) {
            pubSubDomain = false;
            path = path.substring(QUEUE_PREFIX.length());
        }
        else if (path.startsWith(TOPIC_PREFIX)) {
            pubSubDomain = true;
            path = path.substring(TOPIC_PREFIX.length());
        }

        final String subject = convertPathToActualDestination(path);

        // lets make sure we copy the configuration as each endpoint can customize its own version
        JmsEndpoint endpoint = new JmsEndpoint(uri, this, subject, pubSubDomain, getConfiguration().copy());

        URI u = new URI(uri);
        Map options = URISupport.parseParamters(u);
        String selector = (String) options.remove("selector");
        if (selector != null) {
            endpoint.setSelector(selector);
        }
        IntrospectionSupport.setProperties(endpoint.getConfiguration(), options);
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

    public void activate(JmsEndpoint endpoint, Processor<JmsExchange> processor) {
        // TODO Auto-generated method stub
    }

    public void deactivate(JmsEndpoint endpoint) {
        // TODO Auto-generated method stub
    }
}
