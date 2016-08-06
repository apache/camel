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
package org.apache.camel.component.jetty.file;

import java.io.File;
import java.io.FileInputStream;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class JettyFileConsumerTest extends BaseJettyTest {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/binary");
        deleteDirectory("target/test");
        super.setUp();
    }

    private void testingSendingFile(File src) throws Exception {
        deleteDirectory("target/test");
        FileInputStream fis = new FileInputStream(src);
        String response = template.requestBody("http://localhost:{{port}}/myapp/myservice", fis, String.class);
        assertEquals("Response should be OK ", "OK", response);
        File des = new File("target/test/temp.xml");
        assertTrue("The uploaded file should exists", des.exists());
        assertEquals("This two file should have same size", src.length(), des.length());
    }

    @Test
    public void testSending4K() throws Exception {
        File src = new File("src/test/resources/log4j2.properties");
        testingSendingFile(src);
    }
    
    @Test
    @Ignore
    public void testSending18k() throws Exception {
        File src = new File("src/main/java/org/apache/camel/component/jetty/JettyHttpComponent.java");
        testingSendingFile(src);
    }
    
    @Test
    public void testSendBinaryFile() throws Exception {
        deleteDirectory("target/test");
        File jpg = new File("src/test/resources/java.jpg");
        String response = template.requestBody("http://localhost:{{port}}/myapp/myservice2", jpg, String.class);
        assertEquals("Response should be OK ", "OK", response);
        File des = new File("target/test/java.jpg");
        assertTrue("The uploaded file should exists", des.exists());
        assertEquals("This two file should have same size", jpg.length(), des.length());
    }
    
    @Test
    public void testSendBinaryFileUsingCamelRoute() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        File jpg = new File("src/test/resources/java.jpg");
        template.sendBodyAndHeader("file://target/binary", jpg, Exchange.FILE_NAME, "java.jpg");

        assertMockEndpointsSatisfied();
        Thread.sleep(1000);

        File des = new File("target/test/java.jpg");
        assertTrue("The uploaded file should exists", des.exists());
        assertEquals("This two file should have same size", jpg.length(), des.length());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("jetty:http://localhost:{{port}}/myapp/myservice")
                    .to("file://target/test?fileName=temp.xml")
                    .setBody(constant("OK"));
                
                from("jetty:http://localhost:{{port}}/myapp/myservice2")
                    .to("log:foo?showAll=true")
                    .to("file://target/test?fileName=java.jpg")
                    .setBody(constant("OK"));

                from("file://target/binary?noop=true")
                    .to("http://localhost:{{port}}/myapp/myservice2")
                    .to("mock:result");
            }
        };
    }   

}
