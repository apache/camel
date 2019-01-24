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

import java.time.Duration;

import io.nats.client.Connection;
import io.nats.client.Connection.Status;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class NatsProducer extends DefaultProducer {
    
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

        log.debug("Publishing to topic: {}", config.getTopic());
        
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
        log.debug("Starting Nats Producer");
        
        log.debug("Getting Nats Connection");
        connection = getEndpoint().getNatsConfiguration().getConnection() != null 
            ? getEndpoint().getNatsConfiguration().getConnection() : getEndpoint().getConnection();
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("Stopping Nats Producer");
        if (ObjectHelper.isEmpty(getEndpoint().getNatsConfiguration().getConnection())) {
            log.debug("Closing Nats Connection");
            if (connection != null && !connection.getStatus().equals(Status.CLOSED)) {
                if (getEndpoint().getNatsConfiguration().isFlushConnection()) {
                    log.debug("Flushing Nats Connection");
                    connection.flush(Duration.ofMillis(getEndpoint().getNatsConfiguration().getFlushTimeout()));
                }
                connection.close();
            }
        }
        super.doStop();
    }

}
