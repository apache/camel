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

import java.io.InputStream;
import java.net.URL;

import junit.framework.TestCase;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * @version $Revision: 520220 $
 */
public class HttpRouteTest extends TestCase {
	
    public void testPojoRoutes() throws Exception {    	
        CamelContext camelContext = new DefaultCamelContext();
        
        // START SNIPPET: register
        JettyHttpComponent component = new JettyHttpComponent();
        camelContext.addComponent("http", component);
        // END SNIPPET: register
        
        // START SNIPPET: route
        // lets add simple route
        camelContext.addRoutes(new RouteBuilder() {
            public void configure() {
                from("http://0.0.0.0:8080/test").to("mock:a");
            }
        });
        // END SNIPPET: route

        MockEndpoint mockA = (MockEndpoint) camelContext.getEndpoint("mock:a");
        mockA.expectedMessageCount(1);
        
        camelContext.start();
        
        // START SNIPPET: invoke
        URL url = new URL("http://localhost:8080/test");
        InputStream is = url.openConnection().getInputStream();
        System.out.println("Content: "+is);
        // END SNIPPET: invoke
        
        mockA.assertIsSatisfied();
        
        camelContext.stop();
    }
}
