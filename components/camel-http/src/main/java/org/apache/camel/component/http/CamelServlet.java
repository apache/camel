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

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @version $Revision$
 */
public class CamelServlet extends HttpServlet {

    private ConcurrentHashMap<String, HttpConsumer> consumers=new ConcurrentHashMap<String, HttpConsumer>();

	public CamelServlet() {
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
        	        	
        	// Is there a consumer registered for the request.
        	HttpConsumer consumer = resolve(request);
        	if( consumer == null ) {
        		response.sendError(HttpServletResponse.SC_NOT_FOUND);
        		return;
        	}
        	
        	// Have the camel process the HTTP exchange.
			HttpExchange exchange =  new HttpExchange(consumer.getEndpoint(), request, response);			
			consumer.getProcessor().process(exchange);

			// HC: The getBinding() is interesting because it illustrates the impedance miss-match between
			// HTTP's stream oriented protocol, and Camels more message oriented protocol exchanges.

			// now lets output to the response
			consumer.getBinding().writeResponse(exchange);
			
		} catch (Exception e) {
			throw new ServletException(e);
		}
    }

	protected HttpConsumer resolve(HttpServletRequest request) {
		String path = request.getPathInfo();
		return consumers.get(path);
	}

	public void connect(HttpConsumer consumer) {
		consumers.put(consumer.getPath(), consumer);
	}

	public void disconnect(HttpConsumer consumer) {
		consumers.remove(consumer.getPath());
	}

}
