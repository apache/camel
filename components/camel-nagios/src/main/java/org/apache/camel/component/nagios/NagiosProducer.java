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
package org.apache.camel.component.nagios;

import java.util.concurrent.ExecutorService;

import com.googlecode.jsendnsca.Level;
import com.googlecode.jsendnsca.MessagePayload;
import com.googlecode.jsendnsca.NonBlockingNagiosPassiveCheckSender;
import com.googlecode.jsendnsca.PassiveCheckSender;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;

import static org.apache.camel.component.nagios.NagiosConstants.HOST_NAME;
import static org.apache.camel.component.nagios.NagiosConstants.LEVEL;
import static org.apache.camel.component.nagios.NagiosConstants.SERVICE_NAME;

/**
 * @version 
 */
public class NagiosProducer extends DefaultProducer {

    private final PassiveCheckSender sender;

    public NagiosProducer(NagiosEndpoint endpoint, PassiveCheckSender sender) {
        super(endpoint);
        this.sender = sender;
    }

    public void process(Exchange exchange) throws Exception {
        String message = exchange.getIn().getBody(String.class);

        Level level = exchange.getIn().getHeader(LEVEL, Level.class);
        if (level == null) {
            String name = exchange.getIn().getHeader(LEVEL, Level.OK.name(), String.class);
            level = Level.valueOf(name);
        }
        String serviceName = exchange.getIn().getHeader(SERVICE_NAME, exchange.getContext().getName(), String.class);
        String hostName = exchange.getIn().getHeader(HOST_NAME, "localhost", String.class);

        MessagePayload payload = new MessagePayload(hostName, level, serviceName, message);

        if (log.isDebugEnabled()) {
            log.debug("Sending notification to Nagios: {}", payload.getMessage());
        }
        sender.send(payload);
        log.trace("Sending notification done");
    }

    @Override
    protected void doStart() throws Exception {
        // if non blocking then set a executor service on it
        if (sender instanceof NonBlockingNagiosPassiveCheckSender) {
            NonBlockingNagiosPassiveCheckSender nonBlocking = (NonBlockingNagiosPassiveCheckSender) sender;
            ExecutorService executor = getEndpoint().getCamelContext().getExecutorServiceManager()
                    .newSingleThreadExecutor(this, getEndpoint().getEndpointUri());
            nonBlocking.setExecutor(executor);
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        // if non blocking then shutdown executor
        if (sender instanceof NonBlockingNagiosPassiveCheckSender) {
            ((NonBlockingNagiosPassiveCheckSender) sender).shutdown();
        }
    }
}
