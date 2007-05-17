/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.http;

import java.util.HashMap;

import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.security.SslSocketConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;

/**
 * An HttpComponent which starts an embedded Jetty for to handle consuming from
 * http endpoints.
 * 
 * @version $Revision: 525142 $
 */
public class JettyHttpComponent extends HttpComponent {
	
	Server server;
	
	class ConnectorRef {
		Connector connector;
		int refCount = 0;
		public ConnectorRef(Connector connector) {
			this.connector=connector;
			increment();
		}
		public int increment() {
			return ++refCount;
		}
		public int decrement() {
			return --refCount;
		}
	}
	
	final HashMap<String, ConnectorRef> connectors = new HashMap<String, ConnectorRef>();
	
	
	
	
	@Override
	protected void doStart() throws Exception {
		server = createServer();
		super.doStart();
	}

	private Server createServer() throws Exception {
		setCamelServlet(new CamelServlet());
		
		Server server = new Server();
		Context context = new Context(Context.NO_SECURITY|Context.NO_SESSIONS);
		        
		context.setContextPath("/");
		ServletHolder holder = new ServletHolder();
		holder.setServlet(getCamelServlet());
		context.addServlet(holder, "/*");		
		server.setHandler(context);
			
		server.start();
		return server;
	}

	@Override
	protected void doStop() throws Exception {
		for (ConnectorRef connectorRef : connectors.values()) {
			connectorRef.connector.stop();
		}
		connectors.clear();
		
		server.stop();
		super.doStop();
	}

	@Override
	public void connect(HttpConsumer consumer) throws Exception {
		
		// Make sure that there is a connector for the requested endpoint.
		HttpEndpoint endpoint = (HttpEndpoint) consumer.getEndpoint();
		String connectorKey = endpoint.getProtocol()+":"+endpoint.getPort();
		
		synchronized(connectors) {
			ConnectorRef connectorRef = connectors.get(connectorKey);
			if( connectorRef == null ) {
				Connector connector;
				if( "https".equals(endpoint.getProtocol()) ) {
					connector = new SslSocketConnector();
				} else {
					connector = new SelectChannelConnector();
				}
				connector.setPort(endpoint.getPort());
				server.addConnector(connector);
				connector.start();
				connectorRef = new ConnectorRef(connector);
			} else {
				// ref track the connector
				connectorRef.increment();
			}
		}
		
		super.connect(consumer);
	}
	
	@Override
	public void disconnect(HttpConsumer consumer) throws Exception {
		super.disconnect(consumer);
		
		// If the connector is not needed anymore.. then stop it.
		HttpEndpoint endpoint = (HttpEndpoint) consumer.getEndpoint();
		String connectorKey = endpoint.getProtocol()+":"+endpoint.getPort();
		
		synchronized(connectors) {
			ConnectorRef connectorRef = connectors.get(connectorKey);
			if( connectorRef != null ) {
				if( connectorRef.decrement() == 0 ) {
					server.removeConnector(connectorRef.connector);
					connectorRef.connector.stop();
					connectors.remove(connectorKey);
				}
			}
		}
	}
}
