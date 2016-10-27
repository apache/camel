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
package org.apache.camel.component.xmpp;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;

public class XmppConnectionConfigurationFactory {
	
	private String host;
	
	private int port;
	
	private String serviceName;
	
	private SocketFactory socketFactory;
	
	private SecurityMode securityMode;
	
	private boolean isReconnectionAllowed;
	
	private boolean isRosterLoadedAtLogin;
	
	private boolean isSendPresence;

	public void setPort(final int port) {
		this.port = port;
	}
	
	public void setHost(final String host) {
		this.host = host;
	}

	public void setServiceName(final String serviceName) {
		this.serviceName = serviceName;
	}

	public void setSocketFactory(final SocketFactory socketFactory) {
		this.socketFactory = socketFactory;
	}

	public void setSecurityMode(SecurityMode securityMode) {
		this.securityMode = securityMode;
	}

	public void setReconnectionAllowed(final boolean isReconnectionAllowed) {
		this.isReconnectionAllowed = isReconnectionAllowed;
	}

	public void setRosterLoadedAtLogin(final boolean isRosterLoadedAtLogin) {
		this.isRosterLoadedAtLogin = isRosterLoadedAtLogin;
	}

	public void setSendPresence(final boolean isSendPresence) {
		this.isSendPresence = isSendPresence;
	}

	public ConnectionConfiguration createConnectionConfiguration() {
    	final ConnectionConfiguration connectionConfig = new ConnectionConfiguration(host, port, serviceName == null ? host : serviceName);
    	connectionConfig.setSocketFactory(socketFactory == null ? SSLSocketFactory.getDefault() : socketFactory);
    	connectionConfig.setSecurityMode(securityMode == null ? SecurityMode.disabled : securityMode);
    	connectionConfig.setReconnectionAllowed(isReconnectionAllowed);
    	connectionConfig.setRosterLoadedAtLogin(isRosterLoadedAtLogin);
    	connectionConfig.setSendPresence(isSendPresence);
    	return connectionConfig;
	}
}