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
package org.apache.camel.component.servlet;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import com.meterware.servletunit.ServletUnitClient;
import org.junit.Before;
import org.junit.Test;

public class HttpClientRouteExampleSpringTest extends ServletCamelRouterTestSupport {
    @Test
    public void testHttpRestricMethod() throws Exception {
        
        ServletUnitClient client = newClient();
        // Send a web get method request
        WebRequest  req = new GetMethodWebRequest(CONTEXT_URL + "/services/hello");
        WebResponse response = client.getResponse(req);
        
        assertEquals("Get a wrong response message.", "OK", response.getResponseMessage());
        assertEquals("Get a wrong response text.", "Add a name parameter to uri, eg ?name=foo", response.getText());
        
        req = new GetMethodWebRequest(CONTEXT_URL + "/services/hello?name=Willem");
        response = client.getResponse(req);
        assertEquals("Get a wrong response text.", "Hello Willem how are you?", response.getText());
    }

    @Override
    @Before
    public void setUp() throws Exception {
        startCamelContext = false;
        super.setUp();
    }
   
    @Override
    protected String getConfiguration() {
        return "/org/apache/camel/component/servlet/web-example.xml";
    }

}
