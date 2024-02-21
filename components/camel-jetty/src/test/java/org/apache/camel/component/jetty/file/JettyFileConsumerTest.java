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
package org.apache.camel.component.jetty.file;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.time.Duration;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.TestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JettyFileConsumerTest extends BaseJettyTest {
    @TempDir
    Path testDirectory;

    private void testingSendingFile(File src) throws Exception {
        FileInputStream fis = new FileInputStream(src);
        String response = template.requestBody("http://localhost:{{port}}/myapp/myservice", fis, String.class);
        assertEquals("OK", response, "Response should be OK ");
        File des = testDirectory.resolve("test/temp.xml").toFile();
        assertTrue(des.exists(), "The uploaded file should exists");
        assertEquals(src.length(), des.length(), "This two file should have same size");
    }

    @Test
    public void testSending4K() throws Exception {
        File src = new File("src/test/resources/log4j2.properties");
        testingSendingFile(src);
    }

    @Test
    @Disabled
    public void testSending18k() throws Exception {
        File src = new File("src/main/java/org/apache/camel/component/jetty/JettyHttpComponent.java");
        testingSendingFile(src);
    }

    @Test
    public void testSendBinaryFile() {
        File jpg = new File("src/test/resources/java.jpg");
        String response = template.requestBody("http://localhost:{{port}}/myapp/myservice2", jpg, String.class);
        assertEquals("OK", response, "Response should be OK ");
        File des = testDirectory.resolve("test/java.jpg").toFile();
        assertTrue(des.exists(), "The uploaded file should exists");
        assertEquals(jpg.length(), des.length(), "This two file should have same size");
    }

    @Test
    public void testSendBinaryFileUsingCamelRoute() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        File jpg = new File("src/test/resources/java.jpg");
        template.sendBodyAndHeader(TestSupport.fileUri(testDirectory, "binary"), jpg, Exchange.FILE_NAME, "java.jpg");

        MockEndpoint.assertIsSatisfied(context);

        File des = testDirectory.resolve("test/java.jpg").toFile();
        Awaitility.await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertTrue(des.exists(), "The uploaded file should exists"));
        assertEquals(jpg.length(), des.length(), "This two file should have same size");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("jetty:http://localhost:{{port}}/myapp/myservice")
                        .to(TestSupport.fileUri(testDirectory, "test?fileName=temp.xml"))
                        .setBody(constant("OK"));

                from("jetty:http://localhost:{{port}}/myapp/myservice2").to("log:foo?showAll=true")
                        .to(TestSupport.fileUri(testDirectory, "test?fileName=java.jpg")).setBody(constant("OK"));

                from(TestSupport.fileUri(testDirectory, "binary?noop=true")).to("http://localhost:{{port}}/myapp/myservice2")
                        .to("mock:result");
            }
        };
    }

}
