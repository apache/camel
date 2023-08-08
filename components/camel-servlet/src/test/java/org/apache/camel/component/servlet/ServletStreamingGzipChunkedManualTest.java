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

import java.io.File;
import java.io.FileInputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Run this test, and then use curl to call the service such as:
 *
 * curl --output out.gzip http://localhost:50750/mycontext/services/hello
 *
 */
@Disabled("Manual test")
public class ServletStreamingGzipChunkedManualTest extends ServletCamelRouterTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ServletStreamingGzipChunkedManualTest.class);

    private PipedOutputStream pos = new PipedOutputStream();
    private PipedInputStream pis;

    @Test
    public void testManual() throws Exception {
        LOG.info("Sleeping 10 sec");
        Thread.sleep(10000);

        // use background thread to write to stream that camel-servlet uses as response
        context.getExecutorServiceManager().newSingleThreadExecutor(this, "writer").execute(() -> {
            try {
                File file = new File("src/test/data/big-payload.json");
                FileInputStream fis = new FileInputStream(file);

                LOG.info(">>>> sleeping <<<<");
                Thread.sleep(1000);
                LOG.info(">>>> writing <<<<");

                int ch = 0;
                int len = 0;
                while (ch != -1) {
                    ch = fis.read();
                    pos.write(ch);
                    len++;
                    if (len % 1000 == 0) {
                        LOG.info(">>>> sleeping <<<<");
                        pos.flush();
                        Thread.sleep(250);
                        LOG.info(">>>> writing <<<<");
                    }
                }

                LOG.info(">>>> Payload size: {}", len);
                LOG.info(">>>> writing EOL <<<<");
                pos.flush();

            } catch (Exception e) {
                // ignore
            } finally {
                LOG.info(">>>> closing <<<<");
                IOHelper.close(pos);
            }
        });

        LOG.info("Sleeping 60 sec");
        Thread.sleep(60000);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                pis = new PipedInputStream(pos);

                from("servlet:/hello")
                        .setHeader(Exchange.CONTENT_ENCODING, constant("gzip"))
                        .setBody().constant(pis);
            }
        };
    }

}
