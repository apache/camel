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
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;

import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.apache.camel.Exchange.FILE_NAME;
import static org.apache.camel.dataformat.tarfile.TarUtils.TEXT;
import static org.apache.camel.dataformat.tarfile.TarUtils.getBytes;
import static org.apache.camel.dataformat.tarfile.TarUtils.getTaredText;

public class SpringTarFileDataFormatTest extends CamelSpringTestSupport {
    private static final File TEST_DIR = new File("target/springtar");

    @Test
    public void testTarWithoutFileName() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tar");
        mock.expectedMessageCount(1);

        template.sendBody("direct:tar", TEXT);

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertEquals(exchange.getIn().getMessageId() + ".tar", exchange.getIn().getHeader(FILE_NAME));
        assertArrayEquals(getTaredText(exchange.getIn().getMessageId()), (byte[]) exchange.getIn().getBody());
    }


    @Test
    public void testTarWithFileName() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tar");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(FILE_NAME, "poem.txt.tar");

        template.sendBodyAndHeader("direct:tar", TEXT, FILE_NAME, "poem.txt");

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertArrayEquals(getTaredText("poem.txt"), (byte[]) exchange.getIn().getBody());
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
        assertArrayEquals(TEXT.getBytes(UTF_8), (byte[]) exchange.getIn().getBody());
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
        assertArrayEquals(getTaredText(exchange.getIn().getMessageId()), getBytes(file));
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
        assertArrayEquals(getTaredText("poem.txt"), getBytes(file));
    }

    @Test
    public void testDslTar() throws Exception {
        getMockEndpoint("mock:dslTar").expectedBodiesReceived(singletonList(getTaredText("poem.txt")));
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

    @Override
    public void setUp() throws Exception {
        deleteDirectory(TEST_DIR);
        super.setUp();
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/dataformat/tarfile/SpringTarFileDataFormatTest.xml");
    }
}
