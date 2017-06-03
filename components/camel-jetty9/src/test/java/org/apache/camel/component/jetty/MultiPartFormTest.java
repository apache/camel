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
import javax.activation.DataHandler;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.junit.Test;

public class MultiPartFormTest extends BaseJettyTest {
    private RequestEntity createMultipartRequestEntity() throws Exception {
        File file = new File("src/main/resources/META-INF/NOTICE.txt");

        Part[] parts = {new StringPart("comment", "A binary file of some kind"),
                        new FilePart(file.getName(), file)};

        return new MultipartRequestEntity(parts, new HttpMethodParams());

    }

    @Test
    public void testSendMultiPartForm() throws Exception {
        HttpClient httpclient = new HttpClient();

        PostMethod httppost = new PostMethod("http://localhost:" + getPort() + "/test");
        
        httppost.setRequestEntity(createMultipartRequestEntity());

        int status = httpclient.executeMethod(httppost);

        assertEquals("Get a wrong response status", 200, status);
        String result = httppost.getResponseBodyAsString();

        assertEquals("Get a wrong result", "A binary file of some kind", result);

    }

    @Test
    public void testSendMultiPartFormFromCamelHttpComponnent() throws Exception {
        String result = template.requestBody("http://localhost:" + getPort() + "/test", createMultipartRequestEntity(), String.class);
        assertEquals("Get a wrong result", "A binary file of some kind", result);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // Set the jetty temp directory which store the file for multi
                // part form
                // camel-jetty will clean up the file after it handled the
                // request.
                // The option works rightly from Camel 2.4.0
                getContext().getProperties().put("CamelJettyTempDir", "target");

                from("jetty://http://localhost:{{port}}/test").process(new Processor() {

                    public void process(Exchange exchange) throws Exception {
                        Message in = exchange.getIn();
                        assertEquals("Get a wrong attachement size", 2, in.getAttachments().size());
                        // The file name is attachment id
                        DataHandler data = in.getAttachment("NOTICE.txt");

                        assertNotNull("Should get the DataHandle NOTICE.txt", data);
                        // This assert is wrong, but the correct content-type
                        // (application/octet-stream)
                        // will not be returned until Jetty makes it available -
                        // currently the content-type
                        // returned is just the default for FileDataHandler (for
                        // the implentation being used)
                        // assertEquals("Get a wrong content type",
                        // "text/plain", data.getContentType());
                        assertEquals("Got the wrong name", "NOTICE.txt", data.getName());

                        assertTrue("We should get the data from the DataHandle", data.getDataSource()
                            .getInputStream().available() > 0);

                        // The other form date can be get from the message
                        // header

                        // For binary attachment, header should also be populated by DataHandler but not payload
                        Object header = in.getHeader("NOTICE.txt");
                        assertEquals(DataHandler.class, header.getClass());
                        assertEquals(data, header);

                        exchange.getOut().setBody(in.getHeader("comment"));
                    }

                });
                // END SNIPPET: e1
            }
        };
    }

}
