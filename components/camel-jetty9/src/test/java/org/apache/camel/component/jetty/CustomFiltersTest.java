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
package org.apache.camel.component.jetty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.junit.Test;

public class CustomFiltersTest extends BaseJettyTest {

    private static class MyTestFilter implements Filter {
        private String keyWord;
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {            
            // set a marker attribute to show that this filter class was used
            ((HttpServletResponse)response).addHeader("MyTestFilter", "true");
            ((HttpServletResponse)response).setHeader("KeyWord", keyWord);
            chain.doFilter(request, response);
        }

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
            keyWord = filterConfig.getInitParameter("keyWord");
        }

        @Override
        public void destroy() {
            // do nothing here
        }        
    }
    
    private void sendRequestAndVerify(String url) throws Exception {
        HttpClient httpclient = new HttpClient();
        
        PostMethod httppost = new PostMethod(url);
        
        StringRequestEntity reqEntity = new StringRequestEntity("This is a test", null, null);
        httppost.setRequestEntity(reqEntity);

        int status = httpclient.executeMethod(httppost);

        assertEquals("Get a wrong response status", 200, status);

        String result = httppost.getResponseBodyAsString();
        assertEquals("Get a wrong result", "This is a test response", result);
        assertNotNull("Did not use custom multipart filter", httppost.getResponseHeader("MyTestFilter"));

        // just make sure the KeyWord header is set
        assertEquals("Did not set the right KeyWord header", "KEY", httppost.getResponseHeader("KeyWord").getValue());
    }
    
    @Test
    public void testFilters() throws Exception {
        sendRequestAndVerify("http://localhost:" + getPort() + "/testFilters");
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        List<Filter> filters = new ArrayList<Filter>();
        filters.add(new MyTestFilter());
        jndi.bind("myFilters", filters);
        return jndi;
    }
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                                
                // Test the filter list options
                from("jetty://http://localhost:{{port}}/testFilters?filtersRef=myFilters&filterInit.keyWord=KEY").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Message in = exchange.getIn();
                        String request = in.getBody(String.class);
                        // The other form date can be get from the message header
                        exchange.getOut().setBody(request + " response");
                    }
                });
            }
        };
    }
}
