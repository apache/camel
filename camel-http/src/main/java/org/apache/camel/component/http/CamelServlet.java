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

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;

/**
 * @version $Revision$
 */
public class CamelServlet extends HttpServlet {
    private HttpEndpoint endpoint;

    public CamelServlet() {
    }

    public CamelServlet(HttpEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpEndpoint endpoint = resolveEndpoint(request, response);
        if (endpoint == null) {
            throw new ServletException("No endpoint found for request: " + request.getRequestURI());
        }

        HttpExchange exchange = endpoint.createExchange(request, response);
        endpoint.onExchange(exchange);

        // HC: The getBinding() interesting because it illustrates the impedance miss-match between
        // HTTP's stream oriented protocol, and Camels more message oriented protocol exchanges.

        // now lets output to the response
        endpoint.getBinding().writeResponse(exchange);
    }

    protected HttpEndpoint resolveEndpoint(HttpServletRequest request, HttpServletResponse response) {
        return endpoint;
    }
}
