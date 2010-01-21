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

import com.googlecode.jsendnsca.core.Level;
import com.googlecode.jsendnsca.core.MessagePayload;
import com.googlecode.jsendnsca.core.NagiosPassiveCheckSender;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;

import static org.apache.camel.component.nagios.NagiosConstants.HOST_NAME;
import static org.apache.camel.component.nagios.NagiosConstants.LEVEL;
import static org.apache.camel.component.nagios.NagiosConstants.SERIVCE_NAME;

/**
 * @version $Revision$
 */
public class NagiosProducer extends DefaultProducer {

    private final NagiosPassiveCheckSender sender;

    public NagiosProducer(NagiosEndpoint endpoint, NagiosPassiveCheckSender sender) {
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
        String serviceName = exchange.getIn().getHeader(SERIVCE_NAME, exchange.getContext().getName(), String.class);
        String hostName = exchange.getIn().getHeader(HOST_NAME, "localhost", String.class);

        MessagePayload payload = new MessagePayload(hostName, level.ordinal(), serviceName, message);

        if (log.isDebugEnabled()) {
            log.debug("Sending notification to Nagios: " + payload.getMessage());
        }
        sender.send(payload);
        if (log.isTraceEnabled()) {
            log.trace("Sending notification done");
        }
    }
    
}
