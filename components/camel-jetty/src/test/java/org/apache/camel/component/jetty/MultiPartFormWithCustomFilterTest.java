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

import java.io.File;
import java.io.IOException;

import javax.activation.DataHandler;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

public class MultiPartFormWithCustomFilterTest extends BaseJettyTest {

    @BindToRegistry("myMultipartFilter")
    private MyMultipartFilter multipartFilter = new MyMultipartFilter();

    private static class MyMultipartFilter extends MultiPartFilter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            // set a marker attribute to show that this filter class was used
            ((HttpServletResponse)response).addHeader("MyMultipartFilter", "true");

            super.doFilter(request, response, chain);
        }
    }

    @Test
    public void testSendMultiPartForm() throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();

        File file = new File("src/test/resources/log4j2.properties");
        HttpPost httppost = new HttpPost("http://localhost:" + getPort() + "/test");

        HttpEntity entity = MultipartEntityBuilder.create().addTextBody("comment", "A binary file of some kind").addBinaryBody(file.getName(), file).build();
        httppost.setEntity(entity);

        HttpResponse response = client.execute(httppost);
        assertEquals("Get a wrong response status", 200, response.getStatusLine().getStatusCode());
        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

        assertEquals("Get a wrong result", "A binary file of some kind", responseString);
        assertNotNull("Did not use custom multipart filter", response.getFirstHeader("MyMultipartFilter").getValue());

        client.close();
    }

    @Test
    public void testSendMultiPartFormOverrideEnableMultpartFilterFalse() throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();

        File file = new File("src/test/resources/log4j2.properties");

        HttpPost httppost = new HttpPost("http://localhost:" + getPort() + "/test2");
        HttpEntity entity = MultipartEntityBuilder.create().addTextBody("comment", "A binary file of some kind").addBinaryBody(file.getName(), file).build();
        httppost.setEntity(entity);

        HttpResponse response = client.execute(httppost);

        assertEquals("Get a wrong response status", 200, response.getStatusLine().getStatusCode());
        assertNotNull("Did not use custom multipart filter", response.getFirstHeader("MyMultipartFilter").getValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // Set the jetty temp directory which store the file for multi
                // part form
                // camel-jetty will clean up the file after it handled the
                // request.
                // The option works rightly from Camel 2.4.0
                getContext().getGlobalOptions().put("CamelJettyTempDir", "target");

                from("jetty://http://localhost:{{port}}/test?multipartFilterRef=myMultipartFilter").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        AttachmentMessage in = exchange.getMessage(AttachmentMessage.class);
                        assertEquals("Get a wrong attachement size", 2, in.getAttachments().size());
                        // The file name is attachment id
                        DataHandler data = in.getAttachment("log4j2.properties");

                        assertNotNull("Should get the DataHandle log4j2.properties", data);
                        // This assert is wrong, but the correct content-type
                        // (application/octet-stream)
                        // will not be returned until Jetty makes it available -
                        // currently the content-type
                        // returned is just the default for FileDataHandler (for
                        // the implentation being used)
                        // assertEquals("Get a wrong content type",
                        // "text/plain", data.getContentType());
                        assertEquals("Got the wrong name", "log4j2.properties", data.getName());

                        assertTrue("We should get the data from the DataHandle", data.getDataSource().getInputStream().available() > 0);

                        // The other form date can be get from the message
                        // header
                        exchange.getOut().setBody(in.getHeader("comment"));
                    }
                });
                // END SNIPPET: e1

                // Test to ensure that setting a multipartFilterRef overrides
                // the enableMultipartFilter=false parameter
                from("jetty://http://localhost:{{port}}/test2?multipartFilterRef=myMultipartFilter&enableMultipartFilter=false").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        AttachmentMessage in = exchange.getMessage(AttachmentMessage.class);
                        assertEquals("Get a wrong attachement size", 2, in.getAttachments().size());
                        DataHandler data = in.getAttachment("log4j2.properties");

                        assertNotNull("Should get the DataHandle log4j2.properties", data);
                        // The other form date can be get from the message
                        // header
                        exchange.getOut().setBody(in.getHeader("comment"));
                    }
                });
            }
        };
    }
}
