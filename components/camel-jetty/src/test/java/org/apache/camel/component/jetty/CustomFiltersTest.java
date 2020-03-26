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

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
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
        CloseableHttpClient client = HttpClients.createDefault();

        HttpPost httppost = new HttpPost(url);
        httppost.setEntity(new StringEntity("This is a test"));

        HttpResponse response = client.execute(httppost);

        assertEquals("Get a wrong response status", 200, response.getStatusLine().getStatusCode());
        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

        assertEquals("Get a wrong result", "This is a test response", responseString);
        assertEquals("Did not use custom multipart filter", "true", response.getFirstHeader("MyTestFilter").getValue());

        // just make sure the KeyWord header is set
        assertEquals("Did not set the right KeyWord header", "KEY", response.getFirstHeader("KeyWord").getValue());

        client.close();
    }

    @Test
    public void testFilters() throws Exception {
        sendRequestAndVerify("http://localhost:" + getPort() + "/testFilters");
    }

    @BindToRegistry("myFilters")
    public List<Filter> loadFilter() throws Exception {
        List<Filter> filters = new ArrayList<>();
        filters.add(new MyTestFilter());
        return filters;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {

                // Test the filter list options
                from("jetty://http://localhost:{{port}}/testFilters?filters=myFilters&filterInit.keyWord=KEY").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Message in = exchange.getIn();
                        String request = in.getBody(String.class);
                        // The other form date can be get from the message
                        // header
                        exchange.getOut().setBody(request + " response");
                    }
                });
            }
        };
    }
}
