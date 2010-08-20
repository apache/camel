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

import java.io.ByteArrayInputStream;
import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.impl.DefaultHeaderFilterStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.junit.Test;

public class HttpBridgeMultipartRouteTest extends CamelTestSupport {

    private class MultipartHeaderFilterStrategy extends DefaultHeaderFilterStrategy {
        public MultipartHeaderFilterStrategy() {
            initialize();
        }

        protected void initialize() {
            setLowerCase(true);
            setOutFilterPattern("(?i)(Camel|org\\.apache\\.camel)[\\.|a-z|A-z|0-9]*");
        }
    }
    
    @Test
    public void testHttpClient() throws Exception {
        File jpg = new File("src/test/resources/java.jpg");
        String body = "TEST";
        Part[] parts = new Part[] {new StringPart("body", body), new FilePart(jpg.getName(), jpg)};
        
        PostMethod method = new PostMethod("http://localhost:9090/test/hello");
        MultipartRequestEntity requestEntity = new MultipartRequestEntity(parts, method.getParams());
        method.setRequestEntity(requestEntity);
        
        HttpClient client = new HttpClient();
        client.executeMethod(method);
        
        String responseBody = method.getResponseBodyAsString();
        assertEquals(body, responseBody);
        
        String numAttachments = method.getResponseHeader("numAttachments").getValue();
        assertEquals(numAttachments, "1");
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(noErrorHandler());

                Processor serviceProc = new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Message in = exchange.getIn();
                        // put the number of attachments in a response header
                        exchange.getOut().setHeader("numAttachments", in.getAttachments().size());
                        exchange.getOut().setBody(in.getHeader("body"));
                    }
                };
                
                HttpEndpoint epOut = (HttpEndpoint) getContext().getEndpoint("http://localhost:9080?bridgeEndpoint=true&throwExceptionOnFailure=false");
                epOut.setHeaderFilterStrategy(new MultipartHeaderFilterStrategy());
                
                from("jetty:http://localhost:9090/test/hello?enableMultipartFilter=false")
                    .to(epOut);
                
                from("jetty://http://localhost:9080?matchOnUriPrefix=true").process(serviceProc);
            }
        };
    }    

}