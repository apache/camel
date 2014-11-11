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
package org.apache.camel.component.sjms.typeconversion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.Test;

public class JMSMessageHelperTypeConversionTest extends JmsTestSupport {

    private static final String SJMS_QUEUE_URI = "sjms:queue:start";
    private static final String MOCK_RESULT_URI = "mock:result";
    private Exchange message;

    @Test
    public void testJMSMessageHelperString() throws Exception {
        getMockEndpoint(MOCK_RESULT_URI).expectedBodiesReceived("Hello Camel");

        template.sendBody(SJMS_QUEUE_URI, "Hello Camel");
        assertMockEndpointsSatisfied();
        assertTrue(String.class.isInstance(message.getIn().getBody()));
    }
    
    @Test
    public void testJMSMessageHelperInputStream() throws Exception {
        getMockEndpoint(MOCK_RESULT_URI).expectedBodiesReceived("Hello Camel");
        String p = "Hello Camel";
        InputStream is = new ByteArrayInputStream(p.getBytes());
        template.sendBody(SJMS_QUEUE_URI, is);
        assertMockEndpointsSatisfied();
        assertTrue(byte[].class.isInstance(message.getIn().getBody()));
    }
    
    @Test
    public void testJMSMessageHelperByteBuffer() throws Exception {
        getMockEndpoint(MOCK_RESULT_URI).expectedBodiesReceived("Hello Camel");
        String p = "Hello Camel";
        ByteBuffer bb = ByteBuffer.wrap(p.getBytes()); 
        template.sendBody(SJMS_QUEUE_URI, bb);
        assertMockEndpointsSatisfied();
        assertTrue(byte[].class.isInstance(message.getIn().getBody()));
    }
    
    @Test
    public void testJMSMessageHelperFile() throws InterruptedException, IOException {
        getMockEndpoint(MOCK_RESULT_URI).expectedBodiesReceived("Hello Camel");
        String p = "Hello Camel";
        File f = File.createTempFile("tmp-test", ".txt");
        BufferedWriter bw = new BufferedWriter(new FileWriter(f));
        bw.write(p);
        bw.close();
        template.sendBody(SJMS_QUEUE_URI, f);
        assertMockEndpointsSatisfied();
        boolean resultDelete = f.delete();
        assertTrue(resultDelete);
        assertTrue(byte[].class.isInstance(message.getIn().getBody()));
    }
    
    @Test
    public void testJMSMessageHelperReader() throws InterruptedException, IOException {
        getMockEndpoint(MOCK_RESULT_URI).expectedBodiesReceived("Hello Camel");
        String p = "Hello Camel";
        File f = File.createTempFile("tmp-test", ".txt");
        BufferedWriter bw = new BufferedWriter(new FileWriter(f));
        bw.write(p);
        bw.close();
        Reader test = new BufferedReader(new FileReader(f.getAbsolutePath()));
        template.sendBody(SJMS_QUEUE_URI, test);
        assertMockEndpointsSatisfied();
        boolean resultDelete = f.delete();
        assertTrue(resultDelete);
        assertTrue(byte[].class.isInstance(message.getIn().getBody()));
    }
    
    @Test
    public void testJMSMessageHelperStringReader() throws InterruptedException, FileNotFoundException {
        getMockEndpoint(MOCK_RESULT_URI).expectedBodiesReceived("Hello Camel");
        String p = "Hello Camel";
        StringReader test = new StringReader(p);
        template.sendBody(SJMS_QUEUE_URI, test);
        assertMockEndpointsSatisfied();
        assertTrue(byte[].class.isInstance(message.getIn().getBody()));
    }
    
    @Test
    public void testJMSMessageHelperChar() throws InterruptedException, FileNotFoundException {
        getMockEndpoint(MOCK_RESULT_URI).expectedBodiesReceived("H");
        char p = 'H';
        template.sendBody(SJMS_QUEUE_URI, p);
        assertMockEndpointsSatisfied();
        assertTrue(String.class.isInstance(message.getIn().getBody()));
    }
    
    @Test
    public void testJMSMessageHelperCharacter() throws InterruptedException, FileNotFoundException {
        getMockEndpoint(MOCK_RESULT_URI).expectedBodiesReceived("H");
        Character p = 'H';
        template.sendBody(SJMS_QUEUE_URI, p);
        assertMockEndpointsSatisfied();
        assertTrue(String.class.isInstance(message.getIn().getBody()));
    }
    
    @Test
    public void testJMSMessageHelperCharArray() throws InterruptedException, FileNotFoundException {
        getMockEndpoint(MOCK_RESULT_URI).expectedBodiesReceived("Hello Camel");
        char[] p = {'H', 'e', 'l', 'l', 'o', ' ', 'C', 'a', 'm', 'e', 'l'};
        template.sendBody(SJMS_QUEUE_URI, p);
        assertMockEndpointsSatisfied();
        assertTrue(String.class.isInstance(message.getIn().getBody()));
    }
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                interceptSendToEndpoint(MOCK_RESULT_URI).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        message = (Exchange) exchange;
                    }
                });
                
                from(SJMS_QUEUE_URI).to(MOCK_RESULT_URI);
            }
        };
    }
}
