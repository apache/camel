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
import java.util.Properties;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.nats.Connection;
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
        connection.publish(config.getTopic(), body.getBytes());
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
        if (connection != null && connection.isConnected()) {
            connection.close();
        }
    }

    private Connection getConnection() throws IOException, InterruptedException {
        Properties prop = getEndpoint().getNatsConfiguration().createProperties();
        connection = Connection.connect(prop);
        return connection;
    }

}
