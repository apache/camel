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
package org.apache.camel.component.gae.http;

import java.io.InputStream;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.HttpUnitOptions;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import com.meterware.servletunit.ServletRunner;
import com.meterware.servletunit.ServletUnitClient;
import org.apache.camel.component.gae.support.ServletTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import static org.junit.Assert.assertEquals;

public class GHttpInboundRouteBuilderTest extends ServletTestSupport {

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Start servlet container for running the CamelHttpTransportServlet
        String webxml = "org/apache/camel/component/gae/http/web-inbound.xml";
        InputStream is = new ClassPathResource(webxml).getInputStream();
        servletRunner = new ServletRunner(is, CTX_PATH);
        HttpUnitOptions.setExceptionsThrownOnErrorStatus(true);
        is.close();
    }
    
    @Test
    public void testGet() throws Exception {
        WebRequest req = new GetMethodWebRequest(createUrl("/test1?test=input1"));
        ServletUnitClient client = newClient();
        WebResponse response = client.getResponse(req);
        assertEquals("input1", response.getText());
    }
    
    @Test
    public void testPost() throws Exception {
        WebRequest req = new PostMethodWebRequest(createUrl("/test2"), createInput("input2"), getContentType()); 
        ServletUnitClient client = newClient();
        WebResponse response = client.getResponse(req);
        assertEquals("input2", response.getText());
    }
    
}
