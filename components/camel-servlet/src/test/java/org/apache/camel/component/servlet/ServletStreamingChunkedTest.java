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
package org.apache.camel.component.servlet;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ServletStreamingChunkedTest extends ServletCamelRouterTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ServletStreamingChunkedTest.class);

    private PipedOutputStream pos = new PipedOutputStream();
    private PipedInputStream pis;

    @Test
    public void testStreaming() throws Exception {

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        // use background thread to write to stream that camel-servlet uses as reponse
        context.getExecutorServiceManager().newSingleThreadExecutor(this, "writer").execute(() -> {
            try {
                LOG.info(">>>> sleeping <<<<");
                Thread.sleep(500);
                LOG.info(">>>> writing <<<<");
                pos.write("ABC".getBytes());
                pos.flush();

                latch1.await(5, TimeUnit.SECONDS);
                LOG.info(">>>> sleeping <<<<");
                Thread.sleep(500);
                LOG.info(">>>> writing <<<<");
                pos.write("DEF".getBytes());
                pos.flush();

                latch2.await(5, TimeUnit.SECONDS);
                LOG.info(">>>> sleeping <<<<");
                Thread.sleep(500);
                LOG.info(">>>> writing <<<<");
                pos.write("GHI".getBytes());
                pos.flush();

            } catch (Exception e) {
                // ignore
            } finally {
                LOG.info(">>>> closing <<<<");
                IOHelper.close(pos);
            }
        });

        LOG.info(">>>> calling <<<<");
        WebRequest req = new GetMethodWebRequest(contextUrl + "/services/hello");
        WebResponse response = query(req);
        LOG.info(">>>> response <<<<");

        assertEquals(200, response.getResponseCode());
        assertEquals("chunked", response.getHeaderField(Exchange.TRANSFER_ENCODING));

        InputStream is = response.getInputStream();
        assertNotNull(is);
        LOG.info(">>>> reading chunk1 <<<<");
        int ch1 = is.read();
        int ch2 = is.read();
        int ch3 = is.read();
        LOG.info(">>>> reading chunk1 complete <<<<");
        assertEquals('A', ch1);
        assertEquals('B', ch2);
        assertEquals('C', ch3);

        latch1.countDown();

        LOG.info(">>>> reading chunk2 <<<<");
        int ch4 = is.read();
        int ch5 = is.read();
        int ch6 = is.read();
        LOG.info(">>>> reading chunk2 complete <<<<");
        assertEquals('D', ch4);
        assertEquals('E', ch5);
        assertEquals('F', ch6);

        latch2.countDown();

        LOG.info(">>>> reading chunk3 <<<<");
        int ch7 = is.read();
        int ch8 = is.read();
        int ch9 = is.read();
        LOG.info(">>>> reading chunk3 complete <<<<");
        assertEquals('G', ch7);
        assertEquals('H', ch8);
        assertEquals('I', ch9);

        int end = is.read();
        LOG.info(">>>> reading EOS <<<<");
        assertEquals(-1, end);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                pis = new PipedInputStream(pos);

                from("servlet:/hello")
                        .setBody().constant(pis);
            }
        };
    }

}
