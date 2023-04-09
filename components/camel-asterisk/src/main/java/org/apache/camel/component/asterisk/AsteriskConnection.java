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
import org.asteriskjava.manager.ManagerConnection;
import org.asteriskjava.manager.ManagerConnectionFactory;
import org.asteriskjava.manager.ManagerConnectionState;
import org.asteriskjava.manager.ManagerEventListener;
import org.asteriskjava.manager.TimeoutException;
import org.asteriskjava.manager.action.ManagerAction;
import org.asteriskjava.manager.response.ManagerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AsteriskConnection {
    private static final Logger LOG = LoggerFactory.getLogger(AsteriskConnection.class);

    private final String host;
    private final String username;
    private final String password;
    private ManagerConnection managerConnection;

    public AsteriskConnection(String host, String username, String password) {
        this.host = host;
        this.username = username;
        this.password = password;
    }

    public void connect() {
        if (managerConnection == null) {
            LOG.debug("asterisk connection attempt to {} username: {}", host, username);

            ManagerConnectionFactory factory = new ManagerConnectionFactory(host, username, password);
            managerConnection = factory.createManagerConnection();

            LOG.debug("asterisk connection established!");
        }
    }

    public void login()
            throws IllegalStateException, IOException, AuthenticationFailedException, TimeoutException, CamelAsteriskException {
        // Lazy connect if not done before
        connect();

        if (managerConnection != null && (managerConnection.getState() == ManagerConnectionState.DISCONNECTED
                || managerConnection.getState() == ManagerConnectionState.INITIAL)) {
            managerConnection.login("on");

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
}
