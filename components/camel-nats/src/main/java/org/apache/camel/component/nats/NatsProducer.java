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
package org.apache.camel.component.nats;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;

import io.nats.client.Connection;
import io.nats.client.ConnectionFactory;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatsProducer extends DefaultProducer {
    
    private static final Logger LOG = LoggerFactory.getLogger(NatsProducer.class);
    
    private Connection connection;
    
    public NatsProducer(NatsEndpoint endpoint) {
        super(endpoint);
    }
    
    @Override
    public NatsEndpoint getEndpoint() {
        return (NatsEndpoint) super.getEndpoint();
    }
    
    @Override
    public void process(Exchange exchange) throws Exception {
        NatsConfiguration config = getEndpoint().getNatsConfiguration();
        String body = exchange.getIn().getMandatoryBody(String.class);

        LOG.debug("Publishing to topic: {}", config.getTopic());
        
        if (ObjectHelper.isNotEmpty(config.getReplySubject())) {
            String replySubject = config.getReplySubject();
            connection.publish(config.getTopic(), replySubject, body.getBytes());
        } else {
            connection.publish(config.getTopic(), body.getBytes());
        }
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.debug("Starting Nats Producer");
        
        LOG.debug("Getting Nats Connection");
        connection = getConnection();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        LOG.debug("Stopping Nats Producer");
        
        LOG.debug("Closing Nats Connection");
        if (connection != null && !connection.isClosed()) {
            if (getEndpoint().getNatsConfiguration().isFlushConnection()) {
                LOG.debug("Flushing Nats Connection");
                connection.flush(getEndpoint().getNatsConfiguration().getFlushTimeout());
            }
            connection.close();
        }
    }

    private Connection getConnection() throws TimeoutException, IOException, GeneralSecurityException {
        Properties prop = getEndpoint().getNatsConfiguration().createProperties();
        ConnectionFactory factory = new ConnectionFactory(prop);
        if (getEndpoint().getNatsConfiguration().getSslContextParameters() != null && getEndpoint().getNatsConfiguration().isSecure()) {
            SSLContext sslCtx = getEndpoint().getNatsConfiguration().getSslContextParameters().createSSLContext(getEndpoint().getCamelContext()); 
            factory.setSSLContext(sslCtx);
            if (getEndpoint().getNatsConfiguration().isTlsDebug()) {
                factory.setTlsDebug(getEndpoint().getNatsConfiguration().isTlsDebug());
            }
        }
        connection = factory.createConnection();
        return connection;
    }

}
