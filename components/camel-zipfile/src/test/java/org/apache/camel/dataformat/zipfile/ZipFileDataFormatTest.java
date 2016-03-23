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
package org.apache.camel.dataformat.zipfile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.Test;

import static org.apache.camel.Exchange.FILE_NAME;

/**
 * Unit tests for {@link ZipFileDataFormat}.
 */
public class ZipFileDataFormatTest extends CamelTestSupport {

    private static final String TEXT = "The Masque of Queen Bersabe (excerpt) \n"
        + "by: Algernon Charles Swinburne \n\n"
        + "My lips kissed dumb the word of Ah \n"
        + "Sighed on strange lips grown sick thereby. \n"
        + "God wrought to me my royal bed; \n"
        + "The inner work thereof was red, \n"
        + "The outer work was ivory. \n"
        + "My mouth's heat was the heat of flame \n"
        + "For lust towards the kings that came \n"
        + "With horsemen riding royally.";

    private static final File TEST_DIR = new File("target/zip");

    @Test
    public void testZipAndStreamCaching() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:zipStreamCache");
        mock.setExpectedMessageCount(1);

        template.sendBody("direct:zipStreamCache", TEXT);

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertEquals(exchange.getIn().getMessageId() + ".zip", exchange.getIn().getHeader(FILE_NAME));
        assertIsInstanceOf(InputStreamCache.class, exchange.getIn().getBody());
        assertArrayEquals(getZippedText(exchange.getIn().getMessageId()), exchange.getIn().getMandatoryBody(byte[].class));
    }

    @Test
    public void testZipWithoutFileName() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:zip");
        mock.expectedMessageCount(1);

        template.sendBody("direct:zip", TEXT);

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertEquals(exchange.getIn().getMessageId() + ".zip", exchange.getIn().getHeader(FILE_NAME));
        assertArrayEquals(getZippedText(exchange.getIn().getMessageId()), (byte[])exchange.getIn().getBody());
    }

    @Test
    public void testZipWithFileName() throws Exception {
        getMockEndpoint("mock:zip").expectedBodiesReceived(getZippedText("poem.txt"));
        getMockEndpoint("mock:zip").expectedHeaderReceived(FILE_NAME, "poem.txt.zip");

        template.sendBodyAndHeader("direct:zip", TEXT, FILE_NAME, "poem.txt");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUnzip() throws Exception {
        getMockEndpoint("mock:unzip").expectedBodiesReceived(TEXT);
        getMockEndpoint("mock:unzip").expectedHeaderReceived(FILE_NAME, "file");

        template.sendBody("direct:unzip", getZippedText("file"));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testZipAndUnzip() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:zipAndUnzip");
        mock.expectedMessageCount(1);

        template.sendBody("direct:zipAndUnzip", TEXT);

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertEquals(exchange.getIn().getMessageId(), exchange.getIn().getHeader(FILE_NAME));
        assertEquals(TEXT, new String((byte[])exchange.getIn().getBody(), "UTF-8"));
    }

    @Test
    public void testZipToFileWithoutFileName() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        String[] files = TEST_DIR.list();
        assertTrue(files == null || files.length == 0);

        MockEndpoint mock = getMockEndpoint("mock:intercepted");
        mock.expectedMessageCount(1);

        template.sendBody("direct:zipToFile", TEXT);

        assertMockEndpointsSatisfied();

        // use builder to ensure the exchange is fully done before we check for file exists
        assertTrue("The exchange is not done in time.", notify.matches(5, TimeUnit.SECONDS));

        Exchange exchange = mock.getReceivedExchanges().get(0);
        File file = new File(TEST_DIR, exchange.getIn().getMessageId() + ".zip");
        assertTrue("The file should exist.", file.exists());
        assertArrayEquals("Get a wrong message content.", getZippedText(exchange.getIn().getMessageId()), getBytes(file));
    }

    @Test
    public void testZipToFileWithFileName() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        MockEndpoint mock = getMockEndpoint("mock:zipToFile");
        mock.expectedMessageCount(1);
        
        File file = new File(TEST_DIR, "poem.txt.zip");
        assertFalse("The zip should not exit.", file.exists());

        template.sendBodyAndHeader("direct:zipToFile", TEXT, FILE_NAME, "poem.txt");

        // just make sure the file is created
        mock.assertIsSatisfied();

        // use builder to ensure the exchange is fully done before we check for file exists
        assertTrue("The exchange is not done in time.", notify.matches(5, TimeUnit.SECONDS));

        assertTrue("The file should exist.", file.exists());
        assertArrayEquals("Get a wrong message content.", getZippedText("poem.txt"), getBytes(file));
    }

    @Test
    public void testDslZip() throws Exception {
        getMockEndpoint("mock:dslZip").expectedBodiesReceived(getZippedText("poem.txt"));
        getMockEndpoint("mock:dslZip").expectedHeaderReceived(FILE_NAME, "poem.txt.zip");

        template.sendBodyAndHeader("direct:dslZip", TEXT, FILE_NAME, "poem.txt");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDslUnzip() throws Exception {
        getMockEndpoint("mock:dslUnzip").expectedBodiesReceived(TEXT);
        getMockEndpoint("mock:dslUnzip").expectedHeaderReceived(FILE_NAME, "test.txt");

        template.sendBody("direct:dslUnzip", getZippedText("test.txt"));

        assertMockEndpointsSatisfied();
    }

    @Override
    public void setUp() throws Exception {
        deleteDirectory(TEST_DIR);
        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("file:*").to("mock:intercepted");

                ZipFileDataFormat zip = new ZipFileDataFormat();

                from("direct:zip").marshal(zip).to("mock:zip");
                from("direct:unzip").unmarshal(zip).to("mock:unzip");
                from("direct:zipAndUnzip").marshal(zip).unmarshal(zip).to("mock:zipAndUnzip");
                from("direct:zipToFile").marshal(zip).to("file:" + TEST_DIR.getPath()).to("mock:zipToFile");
                from("direct:dslZip").marshal().zipFile().to("mock:dslZip");
                from("direct:dslUnzip").unmarshal().zipFile().to("mock:dslUnzip");
                from("direct:zipStreamCache").streamCaching().marshal().zipFile().to("mock:zipStreamCache");
            }
        };
    }

    private static byte[] getZippedText(String entryName) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(TEXT.getBytes("UTF-8"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        try {
            zos.putNextEntry(new ZipEntry(entryName));
            IOHelper.copy(bais, zos);
        } finally {
            IOHelper.close(bais, zos);
        }
        return baos.toByteArray();
    }

    private static byte[] getBytes(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            IOHelper.copy(fis, baos);
        } finally {
            IOHelper.close(fis, baos);
        }
        return baos.toByteArray();
    }
}
