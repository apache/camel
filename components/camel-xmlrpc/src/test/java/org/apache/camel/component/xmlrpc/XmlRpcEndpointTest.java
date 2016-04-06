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
package org.apache.camel.component.xmlrpc;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.spi.Registry;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.junit.Assert;
import org.junit.Test;

public class XmlRpcEndpointTest extends Assert {
    
    private CamelContext camelContext = new DefaultCamelContext(createRegistry());
    
    protected Registry createRegistry() {
        SimpleRegistry answer = new SimpleRegistry();
        // Binding the client configurer
        answer.put("myClientConfigurer", new MyClientConfigurer());
        return answer;
    }
    
    // create the endpoint with parameters
    @Test
    public void testEndpointSetting() throws Exception {
        camelContext.start();

        XmlRpcEndpoint endpoint = (XmlRpcEndpoint)camelContext.getEndpoint("xmlrpc:http://www.example.com?userAgent=myAgent&gzipCompressing=true&connectionTimeout=30&defaultMethodName=echo");
        XmlRpcClientConfigImpl clientConfig = endpoint.getClientConfig();
        assertEquals("Get a wrong userAgent", "myAgent", clientConfig.getUserAgent());
        assertEquals("Get a wrong gzipCompressing", true, clientConfig.isGzipCompressing());
        assertEquals("Get a wrong connectionTimeout", 30, clientConfig.getConnectionTimeout());
        assertEquals("Get a wrong endpoint address", "http://www.example.com", endpoint.getAddress());
        assertEquals("Get a worng default method name", "echo", endpoint.getDefaultMethodName());
    }
    
    @Test
    public void testClientConfigurer() throws Exception {
        camelContext.start();

        XmlRpcEndpoint endpoint = (XmlRpcEndpoint)camelContext.getEndpoint("xmlrpc:http://www.example.com?clientConfigurer=#myClientConfigurer");
        XmlRpcClient client = endpoint.createClient();
        assertEquals("Get a worng maxThreads", 10, client.getMaxThreads());
        assertEquals("Get a wrong value of enabledForExtensions", true, ((XmlRpcClientConfigImpl)client.getClientConfig()).isEnabledForExtensions());
    }

}
