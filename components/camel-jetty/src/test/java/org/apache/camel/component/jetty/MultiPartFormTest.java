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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

import javax.activation.DataHandler;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Test;

public class MultiPartFormTest extends CamelTestSupport {
    
    @Test
    public void testSendMultiPartForm() throws Exception {
        HttpClient httpclient = new DefaultHttpClient();

        HttpPost httppost = new HttpPost("http://localhost:9080/test");

        FileBody bin = new FileBody(new File("src/main/resources/META-INF/NOTICE.txt"));
        StringBody comment = new StringBody("A binary file of some kind");

        MultipartEntity reqEntity = new MultipartEntity();
        reqEntity.addPart("bin", bin);
        reqEntity.addPart("comment", comment);
        
        httppost.setEntity(reqEntity);
        
        HttpResponse response = httpclient.execute(httppost);
        HttpEntity resEntity = response.getEntity();

        assertEquals("Get a wrong response status", "HTTP/1.1 200 OK", response.getStatusLine().toString());
        assertNotNull("resEntity should not be null", resEntity);
        
        String result = context.getTypeConverter().convertTo(String.class, resEntity.getContent());
        
        assertEquals("Get a wrong result", "A binary file of some kind", result);
        
    }
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
               
                from("jetty://http://localhost:9080/test").process(new Processor() {

                    public void process(Exchange exchange) throws Exception {
                        Message in = exchange.getIn();
                        assertEquals("Get a wrong attachement size", 1, in.getAttachments().size());
                        DataHandler data = in.getAttachment("NOTICE.txt");
                        assertNotNull("Should get the DataHandle NOTICE.txt", data);
                        assertEquals("Get a wrong content type", "text/plain", data.getContentType());
                        exchange.getOut().setBody(in.getHeader("comment"));
                    }
                    
                });
            }
        };
    }


}
