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

import org.asteriskjava.manager.AuthenticationFailedException;
import org.asteriskjava.manager.EventTimeoutException;
import org.asteriskjava.manager.ManagerConnection;
import org.asteriskjava.manager.ManagerConnectionFactory;
import org.asteriskjava.manager.ManagerConnectionState;
import org.asteriskjava.manager.ManagerEventListener;
import org.asteriskjava.manager.ResponseEvents;
import org.asteriskjava.manager.TimeoutException;
import org.asteriskjava.manager.action.EventGeneratingAction;
import org.asteriskjava.manager.action.ManagerAction;
import org.asteriskjava.manager.response.ManagerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AsteriskConnection {
    /**
     * The default port of the Asterisk Manager Interface (AMI).
     */
    public static final int DEFAULT_PORT = 5038;

    private static final Logger LOG = LoggerFactory.getLogger(AsteriskConnection.class);

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private ManagerConnection managerConnection;

    public AsteriskConnection(String host, String username, String password) {
        this(host, DEFAULT_PORT, username, password);
    }

    public AsteriskConnection(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    AsteriskConnection(ManagerConnection managerConnection) {
        this.host = managerConnection.getHostname();
        this.port = managerConnection.getPort();
        this.username = managerConnection.getUsername();
        this.password = managerConnection.getPassword();
        this.managerConnection = managerConnection;
    }

    public void connect() {
        if (managerConnection == null) {
            LOG.debug("asterisk connection attempt to {}:{} username: {}", host, port, username);

            ManagerConnectionFactory factory = new ManagerConnectionFactory(host, port, username, password);
            managerConnection = factory.createManagerConnection();

            LOG.debug("asterisk connection established!");
        }
    }

    /**
     * Logs in with the event mask on, so that Asterisk sends all events to the connection.
     */
    public void login()
            throws IllegalStateException, IOException, AuthenticationFailedException, TimeoutException, CamelAsteriskException {
        login("on");
    }

    /**
     * Logs in with the given event mask, such as on, off, or a list of event classes like system,call.
     */
    public void login(String eventMask)
            throws IllegalStateException, IOException, AuthenticationFailedException, TimeoutException, CamelAsteriskException {
        // Lazy connect if not done before
        connect();

        if (managerConnection != null && (managerConnection.getState() == ManagerConnectionState.DISCONNECTED
                || managerConnection.getState() == ManagerConnectionState.INITIAL)) {
            managerConnection.login(eventMask);

            LOG.debug("asterisk login done!");
        } else {
            throw new CamelAsteriskException("Login operation, managerConnection is empty!");
        }
    }

    public void logoff() throws CamelAsteriskException {
        if (managerConnection != null && managerConnection.getState() == ManagerConnectionState.CONNECTED) {
            managerConnection.logoff();

            LOG.debug("asterisk logoff done!");
        } else {
            throw new CamelAsteriskException("Logoff operation, managerConnection is empty!");
        }
    }

    public void addListener(ManagerEventListener listener) throws CamelAsteriskException {
        if (managerConnection != null) {
            managerConnection.addEventListener(listener);

            LOG.debug("asterisk added listener {}", listener);
        } else {
            throw new CamelAsteriskException("Add listener operation, managerConnection is empty!");
        }
    }

    public void removeListener(ManagerEventListener listener) throws CamelAsteriskException {
        if (managerConnection != null) {
            managerConnection.removeEventListener(listener);

            LOG.debug("asterisk removed listener {}", listener);
        } else {
            throw new CamelAsteriskException("Add listener operation, managerConnection is empty!");
        }
    }

    public ManagerResponse sendAction(ManagerAction action)
            throws IllegalArgumentException, IllegalStateException, IOException, TimeoutException {

        return managerConnection.sendAction(action);
    }

    public ResponseEvents sendEventGeneratingAction(EventGeneratingAction action)
            throws IllegalArgumentException, IllegalStateException, IOException, EventTimeoutException {

        return managerConnection.sendEventGeneratingAction(action);
    }
}
