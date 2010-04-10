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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.junit.Ignore;
import org.junit.Test;

public class MultiPartFormTest extends CamelTestSupport {

    @Test
    @Ignore("Fix me later")
    public void testSendMultiPartForm() throws Exception {
        HttpClient httpclient = new HttpClient();

        File file = new File("src/main/resources/META-INF/NOTICE.txt");

        PostMethod httppost = new PostMethod("http://localhost:9080/test");
        Part[] parts = {
                new StringPart("param_name", "NOTICE.txt"),
                new FilePart(file.getName(), file)
        };

        MultipartRequestEntity reqEntity = new MultipartRequestEntity(parts, httppost.getParams());
        httppost.setRequestEntity(reqEntity);

        int status = httpclient.executeMethod(httppost);

        assertEquals("Get a wrong response status", 200, status);
        String result = httppost.getResponseBodyAsString();

        assertEquals("Get a wrong result", "A binary file of some kind", result);

    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("jetty://http://localhost:9080/test").process(new Processor() {

                    public void process(Exchange exchange) throws Exception {
                        Message in = exchange.getIn();
                        assertEquals("Get a wrong attachement size", 1, in.getAttachments().size());
                        // The file name is attachment id
                        DataHandler data = in.getAttachment("NOTICE.txt");
                        assertNotNull("Should get the DataHandle NOTICE.txt", data);
                        assertEquals("Get a wrong content type", "text/plain", data.getContentType());
                        // The other form date can be get from the message header
                        exchange.getOut().setBody(in.getHeader("comment"));
                    }

                });
                // END SNIPPET: e1
            }
        };
    }


}
