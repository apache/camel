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
package org.apache.camel.component.asterisk;

import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.asteriskjava.manager.AuthenticationFailedException;
import org.asteriskjava.manager.TimeoutException;
import org.asteriskjava.manager.action.ExtensionStateAction;
import org.asteriskjava.manager.action.ManagerAction;
import org.asteriskjava.manager.action.QueueStatusAction;
import org.asteriskjava.manager.action.SipPeersAction;
import org.asteriskjava.manager.response.ManagerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Asterisk producer.
 */
public class AsteriskProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(AsteriskProducer.class);

    private AsteriskEndpoint endpoint;

    public AsteriskProducer(AsteriskEndpoint endpoint) throws IllegalStateException, IOException, AuthenticationFailedException, TimeoutException, CamelAsteriskException {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        endpoint.login();
    }

    @Override
    protected void doStop() throws Exception {
        endpoint.logoff();
    }

    public void process(Exchange exchange) throws Exception {
        ManagerAction action;
        switch (endpoint.getAction()) {
        case QUEUE_STATUS:
            action = new QueueStatusAction();
            break;
        case SIP_PEERS:
            action = new SipPeersAction();
            break;
        case EXTENSION_STATE:
            action = new ExtensionStateAction((String)exchange.getIn().getHeader(AsteriskConstants.EXTENSION), (String)exchange.getIn().getHeader(AsteriskConstants.CONTEXT));
            break;
        default:
            throw new IllegalStateException("Unknown action");
        }

        LOG.debug("Asterisk, send action {} ", endpoint.getAction());

        ManagerResponse response = endpoint.sendAction(action);
        exchange.getIn().setBody(response);
    }

}
