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

import java.io.File;
import java.io.IOException;
import javax.activation.DataHandler;
import javax.servlet.FilterChain;
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
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.eclipse.jetty.servlets.MultiPartFilter;
import org.junit.Test;

public class MultiPartFormWithCustomFilterTest extends BaseJettyTest {

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
        HttpClient httpclient = new HttpClient();
        File file = new File("src/main/resources/META-INF/NOTICE.txt");
        PostMethod httppost = new PostMethod("http://localhost:" + getPort() + "/test");
        Part[] parts = {
            new StringPart("comment", "A binary file of some kind"),
            new FilePart(file.getName(), file)
        };

        MultipartRequestEntity reqEntity = new MultipartRequestEntity(parts, httppost.getParams());
        httppost.setRequestEntity(reqEntity);

        int status = httpclient.executeMethod(httppost);

        assertEquals("Get a wrong response status", 200, status);

        String result = httppost.getResponseBodyAsString();
        assertEquals("Get a wrong result", "A binary file of some kind", result);
        assertNotNull("Did not use custom multipart filter", httppost.getResponseHeader("MyMultipartFilter"));
    }
    
    @Test
    public void testSendMultiPartFormOverrideEnableMultpartFilterFalse() throws Exception {
        HttpClient httpclient = new HttpClient();

        File file = new File("src/main/resources/META-INF/NOTICE.txt");

        PostMethod httppost = new PostMethod("http://localhost:" + getPort() + "/test2");
        Part[] parts = {
            new StringPart("comment", "A binary file of some kind"),
            new FilePart(file.getName(), file)
        };

        MultipartRequestEntity reqEntity = new MultipartRequestEntity(parts, httppost.getParams());
        httppost.setRequestEntity(reqEntity);

        int status = httpclient.executeMethod(httppost);

        assertEquals("Get a wrong response status", 200, status);
        assertNotNull("Did not use custom multipart filter", httppost.getResponseHeader("MyMultipartFilter"));
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myMultipartFilter", new MyMultipartFilter());
        return jndi;
    }
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // Set the jetty temp directory which store the file for multi part form
                // camel-jetty will clean up the file after it handled the request.
                // The option works rightly from Camel 2.4.0
                getContext().getProperties().put("CamelJettyTempDir", "target");
                
                from("jetty://http://localhost:{{port}}/test?multipartFilterRef=myMultipartFilter").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Message in = exchange.getIn();
                        assertEquals("Get a wrong attachement size", 2, in.getAttachments().size());
                        // The file name is attachment id
                        DataHandler data = in.getAttachment("NOTICE.txt");

                        assertNotNull("Should get the DataHandle NOTICE.txt", data);
                        // This assert is wrong, but the correct content-type (application/octet-stream)
                        // will not be returned until Jetty makes it available - currently the content-type
                        // returned is just the default for FileDataHandler (for the implentation being used)
                        //assertEquals("Get a wrong content type", "text/plain", data.getContentType());
                        assertEquals("Got the wrong name", "NOTICE.txt", data.getName());

                        assertTrue("We should get the data from the DataHandle", data.getDataSource()
                            .getInputStream().available() > 0);

                        // The other form date can be get from the message header
                        exchange.getOut().setBody(in.getHeader("comment"));
                    }
                });
                // END SNIPPET: e1

                // Test to ensure that setting a multipartFilterRef overrides the enableMultipartFilter=false parameter
                from("jetty://http://localhost:{{port}}/test2?multipartFilterRef=myMultipartFilter&enableMultipartFilter=false").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Message in = exchange.getIn();
                        assertEquals("Get a wrong attachement size", 2, in.getAttachments().size());
                        DataHandler data = in.getAttachment("NOTICE.txt");

                        assertNotNull("Should get the DataHandle NOTICE.txt", data);
                        // The other form date can be get from the message header
                        exchange.getOut().setBody(in.getHeader("comment"));
                    }
                });
            }
        };
    }
}
