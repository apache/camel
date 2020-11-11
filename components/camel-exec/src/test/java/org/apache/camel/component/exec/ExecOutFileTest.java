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
package org.apache.camel.component.exec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.w3c.dom.Document;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_OUT_FILE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@CamelSpringTest
@ContextConfiguration(locations = { "exec-mock-executor-context.xml" })
public class ExecOutFileTest {

    private static final String FILE_CONTENT = buildFileContent();

    private static final File FILE = new File("target/outfiletest.xml");

    @Produce("direct:input")
    private ProducerTemplate producerTemplate;

    @BeforeEach
    public void setUp() throws IOException {
        FILE.createNewFile();
        FileUtils.writeStringToFile(FILE, FILE_CONTENT, Charset.defaultCharset());
    }

    @AfterEach
    public void tearDown() {
        FileUtils.deleteQuietly(FILE);
    }

    @Test
    @DirtiesContext
    public void testOutFile() throws Exception {
        Exchange e = sendWithMockedExecutor();
        ExecResult result = e.getIn().getBody(ExecResult.class);
        assertNotNull(result);
        File outFile = result.getCommand().getOutFile();
        assertNotNull(outFile);
        assertEquals(FILE_CONTENT, FileUtils.readFileToString(outFile, Charset.defaultCharset()));
    }

    @Test
    @DirtiesContext
    public void testOutFileConvertToInputStream() throws Exception {
        Exchange e = sendWithMockedExecutor();
        InputStream body = e.getIn().getBody(InputStream.class);
        assertNotNull(body);
        assertEquals(FILE_CONTENT, IOUtils.toString(body, Charset.defaultCharset()));
    }

    @Test
    @DirtiesContext
    public void testOutFileConvertToDocument() throws Exception {
        Exchange e = sendWithMockedExecutor();
        Document body = e.getIn().getBody(Document.class);
        assertNotNull(body); // do not parse it
    }

    @Test
    @DirtiesContext
    public void testOutFileConvertToString() throws Exception {
        Exchange e = sendWithMockedExecutor();
        assertEquals(FILE_CONTENT, e.getIn().getBody(String.class));
    }

    @Test
    @DirtiesContext
    public void testOutFileConvertToByteArray() throws Exception {
        Exchange e = sendWithMockedExecutor();
        byte[] body = e.getIn().getBody(byte[].class);
        assertEquals(FILE_CONTENT, new String(body));
    }

    private Exchange sendWithMockedExecutor() {
        Exchange e = producerTemplate.send(new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EXEC_COMMAND_OUT_FILE, FILE.getPath());
                exchange.getIn().setBody(FILE_CONTENT);
            }
        });
        return e;
    }

    private static String buildFileContent() {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append(System.lineSeparator());
        builder.append("<data>").append(System.lineSeparator());
        builder.append("<element>data1</element>").append(System.lineSeparator());
        builder.append("<element>data2</element>").append(System.lineSeparator());
        builder.append("</data>").append(System.lineSeparator());
        builder.append(System.lineSeparator());
        return builder.toString();
    }
}
