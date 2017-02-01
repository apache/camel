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

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.asteriskjava.manager.AuthenticationFailedException;
import org.asteriskjava.manager.ManagerEventListener;
import org.asteriskjava.manager.TimeoutException;
import org.asteriskjava.manager.action.ManagerAction;
import org.asteriskjava.manager.event.ManagerEvent;
import org.asteriskjava.manager.response.ManagerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The asterisk component is used to interact with Asterisk PBX Server <a href="http://www.asterisk.org">Asterisk PBX Server</a>.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "asterisk", title = "Asterisk", syntax = "asterisk:name", consumerClass = AsteriskConsumer.class, label = "voip")
public class AsteriskEndpoint extends DefaultEndpoint {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(AsteriskProducer.class);

    private AsteriskConnection asteriskConnection;

    @UriPath(description = "Name of component") @Metadata(required = "true")
    private String name;
    @UriParam
    private String hostname;
    @UriParam(label = "producer")
    private AsteriskActionEnum action;
    @UriParam(secret = true)
    private String username;
    @UriParam(secret = true)
    private String password;

    public AsteriskEndpoint(String uri, AsteriskComponent component) {
        super(uri, component);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        asteriskConnection = new AsteriskConnection(hostname, username, password);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        // do not exist disconnect operation!!!
        asteriskConnection = null;
    }

    public Producer createProducer() throws Exception {
        if (action == null) {
            throw new IllegalArgumentException("Missing required action parameter");
        }
        
        return new AsteriskProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new AsteriskConsumer(this, processor);
    }

    public boolean isSingleton() {
        // TODO: prefer to be singleton and do not have state on the endpoint
        // the asteriskConnection should be createed on the consumer / producer instance and be private there
        return false;
    }

    public void addListener(ManagerEventListener listener) throws CamelAsteriskException {
        asteriskConnection.addListener(listener);
    }

    public void login() throws IllegalStateException, IOException, AuthenticationFailedException, TimeoutException, CamelAsteriskException {
        asteriskConnection.login();
    }

    public void logoff() throws CamelAsteriskException {
        asteriskConnection.logoff();
    }

    public Exchange createExchange(ManagerEvent event) {
        Exchange exchange = super.createExchange();
        exchange.getIn().setHeader(AsteriskConstants.EVENT_NAME, event.getClass().getSimpleName());
        exchange.getIn().setBody(event);
        return exchange;
    }

    public ManagerResponse sendAction(ManagerAction action) throws IllegalArgumentException, IllegalStateException, IOException, TimeoutException {
        return asteriskConnection.sendAction(action);
    }

    public String getUsername() {
        return username;
    }

    /**
     * Login username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Login password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public AsteriskActionEnum getAction() {
        return action;
    }

    /**
     * What action to perform such as getting queue status, sip peers or extension state.
     */
    public void setAction(AsteriskActionEnum action) {
        this.action = action;
    }

    public String getHostname() {
        return hostname;
    }

    /**
     * The hostname of the asterix server
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * Logical name
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
