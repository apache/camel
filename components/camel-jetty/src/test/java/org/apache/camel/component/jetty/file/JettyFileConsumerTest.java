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
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

public class JettyFileConsumerTest extends CamelTestSupport {
    
    private void testingSendingFile(File src) throws Exception {
        deleteDirectory("target/file");
        FileInputStream fis = new FileInputStream(src);
        String response = template.requestBody("http://localhost:9080/myapp/myservice", fis, String.class);
        assertEquals("Response should be OK ", "OK", response);
        File des = new File("target/test/temp.xml");
        assertTrue("The uploaded file should exists", des.exists());
        assertEquals("This two file should have same size", src.length(), des.length());
    }
    
    
    @Test
    public void testSending4K() throws Exception {
        File src = new File("src/main/java/org/apache/camel/component/jetty/CamelContinuationServlet.java");
        testingSendingFile(src);
        
    }
    
    @Test
    public void testSending18k() throws Exception {
        File src = new File("src/main/java/org/apache/camel/component/jetty/JettyHttpComponent.java");
        testingSendingFile(src);
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("jetty:http://localhost:9080/myapp/myservice")
                    .to("file://target/test?fileName=temp.xml")
                    .setBody(constant("OK"));
                    
            }
        };
    }   

}
