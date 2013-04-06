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
package org.apache.camel.component.exec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.w3c.dom.Document;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.apache.camel.component.exec.ExecBinding.EXEC_COMMAND_OUT_FILE;
import static org.apache.commons.io.IOUtils.LINE_SEPARATOR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@ContextConfiguration(locations = {"exec-mock-executor-context.xml"})
public class ExecOutFileTest extends AbstractJUnit4SpringContextTests {

    private static final String FILE_CONTENT = buildFileContent();

    private static final File FILE = new File("target/outfiletest.xml");

    @Produce(uri = "direct:input")
    private ProducerTemplate producerTemplate;

    @Before
    public void setUp() throws IOException {
        FILE.createNewFile();
        FileUtils.writeStringToFile(FILE, FILE_CONTENT);
    }

    @After
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
        assertEquals(FILE_CONTENT, FileUtils.readFileToString(outFile));
    }

    @Test
    @DirtiesContext
    public void testOutFileConvertToInputStream() throws Exception {
        Exchange e = sendWithMockedExecutor();
        InputStream body = e.getIn().getBody(InputStream.class);
        assertNotNull(body);
        assertEquals(FILE_CONTENT, IOUtils.toString(body));
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
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append(LINE_SEPARATOR);
        builder.append("<data>").append(LINE_SEPARATOR);
        builder.append("<element>data1</element>").append(LINE_SEPARATOR);
        builder.append("<element>data2</element>").append(LINE_SEPARATOR);
        builder.append("</data>").append(LINE_SEPARATOR);
        builder.append(LINE_SEPARATOR);
        return builder.toString();
    }
}
