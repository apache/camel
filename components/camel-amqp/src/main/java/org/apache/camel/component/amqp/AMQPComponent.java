/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.amqp;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.qpid.amqp_1_0.jms.impl.ConnectionFactoryImpl;
import org.apache.qpid.client.AMQConnectionFactory;

/**
 * This component supports the AMQP protocol using the Client API of the Apache Qpid project.
 */
public class AMQPComponent extends JmsComponent {

    public AMQPComponent() {
        super(AMQPEndpoint.class);
    }

    public AMQPComponent(JmsConfiguration configuration) {
        super(configuration);
    }

    public AMQPComponent(CamelContext context) {
        super(context, AMQPEndpoint.class);
    }

    public AMQPComponent(ConnectionFactory connectionFactory) {
        setConnectionFactory(connectionFactory);
    }

    public static Component amqpComponent(String uri, boolean old) throws MalformedURLException, URISyntaxException {
        if (old) {
            return amqpComponentOld(uri);
        }
        return new AMQPComponent(ConnectionFactoryImpl.createFromURL(uri));
    }

    public static Component amqpComponentOld(String uri) throws URISyntaxException {
        return new AMQPComponent(new AMQConnectionFactory(uri));
    }

    public static Component amqpComponent(String uri) throws MalformedURLException {
        return new AMQPComponent(ConnectionFactoryImpl.createFromURL(uri));
    }

    public static AMQPComponent amqp10Component(String uri) throws MalformedURLException {
        ConnectionFactoryImpl connectionFactory = ConnectionFactoryImpl.createFromURL(uri);
        connectionFactory.setTopicPrefix("topic://");
        return new AMQPComponent(connectionFactory);
    }

}
