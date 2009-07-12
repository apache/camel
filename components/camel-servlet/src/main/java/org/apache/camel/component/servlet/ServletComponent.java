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
package org.apache.camel.component.servlet;

import java.net.URI;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.http.CamelServlet;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.component.http.HttpConsumer;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.commons.httpclient.params.HttpClientParams;

public class ServletComponent extends HttpComponent {
    
    private CamelServlet camelServlet;
    
    public void setCamelServlet(CamelServlet servlet) {
        camelServlet = servlet;
    }
        
    
    public CamelServlet getCamelServlet(String servletName) {
        CamelServlet answer = null;
        if (camelServlet == null) {
            answer = CamelHttpTransportServlet.getCamelServlet(servletName);
        } else {
            answer = camelServlet;
        }
        if (answer == null) {
            throw new IllegalArgumentException("Can't find the deployied servlet, please set the ServletComponent with it"
                + " or delopy a CamelHttpTransportServlet int the web container");
        }
        return answer;
    }
    
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        uri = uri.startsWith("servlet:") ? remaining : uri;

        HttpClientParams params = new HttpClientParams();
        IntrospectionSupport.setProperties(params, parameters, "httpClient.");

        // configure regular parameters
        configureParameters(parameters);

        // restructure uri to be based on the parameters left as we dont want to include the Camel internal options
        URI httpUri = URISupport.createRemainingURI(new URI(UnsafeUriCharactersEncoder.encode(uri)), parameters);
        uri = httpUri.toString();

        ServletEndpoint result = new ServletEndpoint(uri, this, httpUri, params, getHttpConnectionManager(), httpClientConfigurer);
        if (httpBinding != null) {
            result.setBinding(httpBinding);
        }
        setEndpointHeaderFilterStrategy(result);        
        setProperties(result, parameters);
        return result;
    }

    
 
    public void connect(HttpConsumer consumer) throws Exception {
        ServletEndpoint endpoint = (ServletEndpoint) consumer.getEndpoint();
        CamelServlet servlet = getCamelServlet(endpoint.getServletName());
        ObjectHelper.notNull(servlet, "CamelServlet");
        servlet.connect(consumer);
    }

    public void disconnect(HttpConsumer consumer) throws Exception {
        ServletEndpoint endpoint = (ServletEndpoint) consumer.getEndpoint();
        CamelServlet servlet = getCamelServlet(endpoint.getServletName());
        ObjectHelper.notNull(servlet, "CamelServlet");
        servlet.disconnect(consumer);
    }

}