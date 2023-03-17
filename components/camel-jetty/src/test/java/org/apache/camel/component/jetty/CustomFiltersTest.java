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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CustomFiltersTest extends BaseJettyTest {

    private static class MyTestFilter implements Filter {
        private String keyWord;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            // set a marker attribute to show that this filter class was used
            ((HttpServletResponse) response).addHeader("MyTestFilter", "true");
            ((HttpServletResponse) response).setHeader("KeyWord", keyWord);
            chain.doFilter(request, response);
        }

        @Override
        public void init(FilterConfig filterConfig) {
            keyWord = filterConfig.getInitParameter("keyWord");
        }

        @Override
        public void destroy() {
            // do nothing here
        }
    }

    private void sendRequestAndVerify(String url) throws Exception {
        HttpPost httppost = new HttpPost(url);
        httppost.setEntity(new StringEntity("This is a test"));

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(httppost)) {

            assertEquals(200, response.getCode(), "Get a wrong response status");
            String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

            assertEquals("This is a test response", responseString, "Get a wrong result");
            assertEquals("true", response.getFirstHeader("MyTestFilter").getValue(), "Did not use custom multipart filter");

            // just make sure the KeyWord header is set
            assertEquals("KEY", response.getFirstHeader("KeyWord").getValue(), "Did not set the right KeyWord header");
        }
    }

    @Test
    public void testFilters() throws Exception {
        sendRequestAndVerify("http://localhost:" + getPort() + "/testFilters");
    }

    @BindToRegistry("myFilters")
    public List<Filter> loadFilter() {
        List<Filter> filters = new ArrayList<>();
        filters.add(new MyTestFilter());
        return filters;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                // Test the filter list options
                from("jetty://http://localhost:{{port}}/testFilters?filters=myFilters&filterInit.keyWord=KEY")
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                Message in = exchange.getIn();
                                String request = in.getBody(String.class);
                                // The other form date can be get from the message
                                // header
                                exchange.getMessage().setBody(request + " response");
                            }
                        });
            }
        };
    }
}
