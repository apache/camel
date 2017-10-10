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
package org.apache.camel.dataformat.tarfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.Test;

import static org.apache.camel.Exchange.FILE_NAME;
import static org.apache.camel.dataformat.tarfile.TarUtils.TEXT;
import static org.apache.camel.dataformat.tarfile.TarUtils.getBytes;
import static org.apache.camel.dataformat.tarfile.TarUtils.getTaredText;
import static org.apache.camel.dataformat.tarfile.TarUtils.getTaredTextInFolder;

/**
 * Unit tests for {@link TarFileDataFormat}.
 */
public class TarFileDataFormatTest extends CamelTestSupport {

    private static final File TEST_DIR = new File("target/tar");
    private TarFileDataFormat tar;

    @Test
    public void testTarWithoutFileName() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tar");
        mock.expectedMessageCount(1);

        template.sendBody("direct:tar", TEXT);

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertEquals(exchange.getIn().getMessageId() + ".tar", exchange.getIn().getHeader(FILE_NAME));
        assertTrue(ObjectHelper.equalByteArray(getTaredText(exchange.getIn().getMessageId()), (byte[]) exchange.getIn().getBody()));
    }

    @Test
    public void testTarWithFileName() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tar");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(FILE_NAME, "poem.txt.tar");

        template.sendBodyAndHeader("direct:tar", TEXT, FILE_NAME, "poem.txt");

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertTrue(ObjectHelper.equalByteArray(getTaredText("poem.txt"), (byte[]) exchange.getIn().getBody()));
    }

    @Test
    public void testTarWithPathElements() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tar");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(FILE_NAME, "poem.txt.tar");

        template.sendBodyAndHeader("direct:tar", TEXT, FILE_NAME, "poems/poem.txt");

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertTrue(ObjectHelper.equalByteArray(getTaredText("poem.txt"), (byte[]) exchange.getIn().getBody()));
    }

    @Test
    public void testTarWithPreservedPathElements() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tar");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(FILE_NAME, "poem.txt.tar");

        tar.setPreservePathElements(true);

        template.sendBodyAndHeader("direct:tar", TEXT, FILE_NAME, "poems/poem.txt");

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertTrue(ObjectHelper.equalByteArray(getTaredTextInFolder("poems/", "poems/poem.txt"), (byte[]) exchange.getIn().getBody()));
    }

    @Test
    public void testUntar() throws Exception {
        getMockEndpoint("mock:untar").expectedBodiesReceived(TEXT);
        getMockEndpoint("mock:untar").expectedHeaderReceived(FILE_NAME, "file");

        template.sendBody("direct:untar", getTaredText("file"));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTarAndUntar() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tarAndUntar");
        mock.expectedMessageCount(1);

        template.sendBody("direct:tarAndUntar", TEXT);

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertEquals(exchange.getIn().getMessageId(), exchange.getIn().getHeader(FILE_NAME));
        assertEquals(TEXT, new String((byte[]) exchange.getIn().getBody(), "UTF-8"));
    }

    @Test
    public void testTarToFileWithoutFileName() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        String[] files = TEST_DIR.list();
        assertTrue(files == null || files.length == 0);

        MockEndpoint mock = getMockEndpoint("mock:intercepted");
        mock.expectedMessageCount(1);

        template.sendBody("direct:tarToFile", TEXT);

        assertMockEndpointsSatisfied();

        // use builder to ensure the exchange is fully done before we check for file exists
        assertTrue(notify.matches(5, TimeUnit.SECONDS));

        Exchange exchange = mock.getReceivedExchanges().get(0);
        File file = new File(TEST_DIR, exchange.getIn().getMessageId() + ".tar");
        assertTrue(file.exists());
        assertTrue(ObjectHelper.equalByteArray(getTaredText(exchange.getIn().getMessageId()), getBytes(file)));
    }

    @Test
    public void testTarToFileWithFileName() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        MockEndpoint mock = getMockEndpoint("mock:tarToFile");
        mock.expectedMessageCount(1);

        File file = new File(TEST_DIR, "poem.txt.tar");
        assertFalse(file.exists());

        template.sendBodyAndHeader("direct:tarToFile", TEXT, FILE_NAME, "poem.txt");

        // just make sure the file is created
        mock.assertIsSatisfied();

        // use builder to ensure the exchange is fully done before we check for file exists
        assertTrue(notify.matches(5, TimeUnit.SECONDS));

        assertTrue(file.exists());
        assertTrue(ObjectHelper.equalByteArray(getTaredText("poem.txt"), getBytes(file)));
    }

    @Test
    public void testDslTar() throws Exception {
        getMockEndpoint("mock:dslTar").expectedBodiesReceived(getTaredText("poem.txt"));
        getMockEndpoint("mock:dslTar").expectedHeaderReceived(FILE_NAME, "poem.txt.tar");

        template.sendBodyAndHeader("direct:dslTar", TEXT, FILE_NAME, "poem.txt");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDslUntar() throws Exception {
        getMockEndpoint("mock:dslUntar").expectedBodiesReceived(TEXT);
        getMockEndpoint("mock:dslUntar").expectedHeaderReceived(FILE_NAME, "test.txt");

        template.sendBody("direct:dslUntar", getTaredText("test.txt"));

        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testUntarWithEmptyDirectorySupported() throws Exception {
        deleteDirectory(new File("hello_out"));
        tar.setUsingIterator(true);
        tar.setAllowEmptyDirectory(true);
        template.sendBody("direct:untarWithEmptyDirectory", new File("src/test/resources/data/hello.tar"));
        assertTrue(Files.exists(Paths.get("hello_out/Configurations2")));
        deleteDirectory(new File("hello_out"));
    }
    
    @Test
    public void testUntarWithEmptyDirectoryUnsupported() throws Exception {
        deleteDirectory(new File("hello_out"));
        tar.setUsingIterator(true);
        tar.setAllowEmptyDirectory(false);
        template.sendBody("direct:untarWithEmptyDirectory", new File("src/test/resources/data/hello.tar"));
        assertTrue(!Files.exists(Paths.get("hello_out/Configurations2")));
        deleteDirectory(new File("hello_out"));
    }

    @Override
    public void setUp() throws Exception {
        deleteDirectory(TEST_DIR);
        super.setUp();
    }
    
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        while (true) {
            int readCount = in.read(buffer);
            if (readCount < 0) {
                break;
            }
            out.write(buffer, 0, readCount);
        }
    }

    private static void copy(File file, OutputStream out) throws IOException { 
        try (InputStream in = new FileInputStream(file)) {
            copy(in, out); 
        } 
    }

    private static void copy(InputStream in, File file) throws IOException { 
        try (OutputStream out = new FileOutputStream(file)) {
            copy(in, out); 
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("file:*").to("mock:intercepted");

                tar = new TarFileDataFormat();

                from("direct:tar").marshal(tar).to("mock:tar");
                from("direct:untar").unmarshal(tar).to("mock:untar");
                from("direct:untarWithEmptyDirectory").unmarshal(tar)
                                         .split(body(Iterator.class))
                                         //.streaming()
                                         //.to("file:hello_out?autoCreate=true")
                                         .process(new Processor() {
                                             @Override
                                             public void process(Exchange exchange) throws Exception {
                                                 InputStream is = new FileInputStream("src/test/resources/data/hello.tar"); 

                                                 TarArchiveEntry entry = new TarArchiveEntry((String)exchange.getIn().getHeader(Exchange.FILE_NAME)); 
                                                 File outputFile = new File("hello_out", entry.getName());
                                                 if (entry.isDirectory()) {
                                                     outputFile.mkdirs();
                                                 } else {
                                                     outputFile.getParentFile().mkdirs();
                                                     TarArchiveInputStream debInputStream = (TarArchiveInputStream) 
                                                             new ArchiveStreamFactory().createArchiveInputStream("tar", is);
                                                     try {
                                                         copy(debInputStream, outputFile);
                                                     } finally {
                                                         debInputStream.close();
                                                     }
                                                 }
                                             }
                                         })
                                   .end();
                from("direct:tarAndUntar").marshal(tar).unmarshal(tar).to("mock:tarAndUntar");
                from("direct:tarToFile").marshal(tar).to("file:" + TEST_DIR.getPath()).to("mock:tarToFile");
                from("direct:dslTar").marshal(tar).to("mock:dslTar");
                from("direct:dslUntar").unmarshal(tar).to("mock:dslUntar");
            }
        };
    }
}
