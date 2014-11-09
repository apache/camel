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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.Test;

public class JMSMessageHelperTypeConversionTest extends JmsTestSupport {

    private static final String SJMS_QUEUE_URI = "sjms:queue:start";
    private static final String MOCK_RESULT_URI = "mock:result";

    @Test
    public void testJMSMessageHelperString() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Camel");

        template.sendBody("sjms:queue:start", "Hello Camel");
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testJMSMessageHelperInputStream() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Camel");
        String p = "Hello Camel";
        InputStream is = new ByteArrayInputStream( p.getBytes() );
        template.sendBody("sjms:queue:start", is);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testJMSMessageHelperByteBuffer() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Camel");
        String p = "Hello Camel";
        ByteBuffer bb = ByteBuffer.wrap(p.getBytes()); 
        template.sendBody("sjms:queue:start", bb);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testJMSMessageHelperFile() throws InterruptedException, FileNotFoundException {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Camel");
        String p = "Hello Camel";
        FileOutputStream prova = null;
		prova = new FileOutputStream("test.txt");
		assertNotNull(prova);
        PrintStream scrivi = new PrintStream(prova);
        scrivi.print(p);
        scrivi.close();
        File f = new File("test.txt");
        template.sendBody("sjms:queue:start", f);
        assertMockEndpointsSatisfied();
        boolean result = f.delete();
        assertTrue(result);
    }
    
    @Test
    public void testJMSMessageHelperReader() throws InterruptedException, FileNotFoundException {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Camel");
        String p = "Hello Camel";
        FileOutputStream prova = null;
		prova = new FileOutputStream("test.txt");
		assertNotNull(prova);
        PrintStream scrivi = new PrintStream(prova);
        scrivi.print(p);
        scrivi.close();
        Reader test = new BufferedReader(new FileReader("test.txt"));
        template.sendBody("sjms:queue:start", test);
        assertMockEndpointsSatisfied();
        boolean result = (new File("test.txt")).delete();
        assertTrue(result);
    }
    
    @Test
    public void testJMSMessageHelperStringReader() throws InterruptedException, FileNotFoundException {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Camel");
        String p = "Hello Camel";
        StringReader test = new StringReader(p);
        template.sendBody("sjms:queue:start", test);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testJMSMessageHelperChar() throws InterruptedException, FileNotFoundException {
        getMockEndpoint("mock:result").expectedBodiesReceived("H");
        char p = 'H';
        template.sendBody("sjms:queue:start", p);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testJMSMessageHelperCharArray() throws InterruptedException, FileNotFoundException {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Camel");
        char[] p = {'H','e','l','l','o',' ','C','a','m','e','l'};
        template.sendBody("sjms:queue:start", p);
        assertMockEndpointsSatisfied();
    }
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(SJMS_QUEUE_URI).to(MOCK_RESULT_URI);
            }
        };
    }
}
