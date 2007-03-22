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

import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.Processor;
import org.apache.camel.CamelContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Represents a HTTP based Endpoint
 *
 * @version $Revision$
 */
public class HttpEndpoint extends DefaultEndpoint<HttpExchange> {

    private HttpBinding binding;

    protected HttpEndpoint(String uri, CamelContext camelContext) {
        super(uri, camelContext);
    }

    public void onExchange(HttpExchange exchange) {
        Processor<HttpExchange> processor = getInboundProcessor();
        if (processor != null) {
            // lets route straight to our processor
            processor.onExchange(exchange);
        }
        else {
            // we need an external HTTP client such as commons-httpclient
            // TODO
        }
    }

    public HttpExchange createExchange() {
        return new HttpExchange(getContext());
    }

    public HttpExchange createExchange(HttpServletRequest request, HttpServletResponse response) {
        return new HttpExchange(getContext(), request, response);
    }

    public HttpBinding getBinding() {
        if (binding == null) {
            binding = new HttpBinding();
        }
        return binding;
    }

    public void setBinding(HttpBinding binding) {
        this.binding = binding;
    }
}
