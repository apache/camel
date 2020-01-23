/*
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
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.asteriskjava.manager.AuthenticationFailedException;
import org.asteriskjava.manager.TimeoutException;
import org.asteriskjava.manager.action.ManagerAction;
import org.asteriskjava.manager.response.ManagerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Asterisk producer.
 */
public class AsteriskProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(AsteriskProducer.class);

    private final AsteriskEndpoint endpoint;
    private final AsteriskConnection connection;

    public AsteriskProducer(AsteriskEndpoint endpoint) throws IllegalStateException, IOException, AuthenticationFailedException, TimeoutException, CamelAsteriskException {
        super(endpoint);

        this.endpoint = endpoint;
        this.connection = new AsteriskConnection(endpoint.getHostname(), endpoint.getUsername(), endpoint.getPassword());
    }

    @Override
    protected void doStart() throws Exception {
        connection.login();

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        connection.logoff();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // The action set in the URI can be overridden using the message
        // header CamelAsteriskAction
        AsteriskAction action = exchange.getIn().getHeader(AsteriskConstants.ACTION, AsteriskAction.class);
        if (action == null) {
            action = endpoint.getAction();
        }

        // Action must be set
        ObjectHelper.notNull(action, "action");

        LOG.debug("Send action {}", action);

        ManagerAction managerAction = action.apply(exchange);
        ManagerResponse managerResponse = connection.sendAction(managerAction);

        exchange.getIn().setBody(managerResponse);
    }

}
