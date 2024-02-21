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
package org.apache.camel.builder.saxon;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.junit5.TestSupport;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
public class XPathSplitChoicePerformanceTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(XPathSplitChoicePerformanceTest.class);

    @TempDir
    Path testDirectory;

    private int size = 20 * 1000;
    private final AtomicInteger tiny = new AtomicInteger();
    private final AtomicInteger small = new AtomicInteger();
    private final AtomicInteger med = new AtomicInteger();
    private final AtomicInteger large = new AtomicInteger();
    private final StopWatch watch = new StopWatch();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        createDataFile(LOG, size);
        super.setUp();
    }

    @Test
    @Disabled("Manual test")
    public void testXPathPerformanceRoute() {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(size).create();

        boolean matches = notify.matches(60, TimeUnit.SECONDS);
        LOG.info("Processed file with {} elements in: {}", size, TimeUtils.printDuration(watch.taken(), true));

        LOG.info("Processed {} tiny messages", tiny.get());
        LOG.info("Processed {} small messages", small.get());
        LOG.info("Processed {} medium messages", med.get());
        LOG.info("Processed {} large messages", large.get());

        assertEquals((size / 10) * 4, tiny.get());
        assertEquals((size / 10) * 2, small.get());
        assertEquals((size / 10) * 3, med.get());
        assertEquals((size / 10) * 1, large.get());

        assertTrue(matches, "Should complete route");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(TestSupport.fileUri(testDirectory, "?noop=true"))
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                log.info("Starting to process file");
                                watch.restart();
                            }
                        })
                        .split().xpath("/orders/order").streaming()
                        .choice()
                        .when().xpath("/order/amount < 10")
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                String xml = exchange.getIn().getBody(String.class);
                                assertTrue(xml.contains("<amount>3</amount>"), xml);

                                int num = tiny.incrementAndGet();
                                if (num % 100 == 0) {
                                    log.info("Processed {} tiny messages", num);
                                    log.debug(xml);
                                }
                            }
                        })
                        .when().xpath("/order/amount < 50")
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                String xml = exchange.getIn().getBody(String.class);
                                assertTrue(xml.contains("<amount>44</amount>"), xml);

                                int num = small.incrementAndGet();
                                if (num % 100 == 0) {
                                    log.info("Processed {} small messages", num);
                                    log.debug(xml);
                                }
                            }
                        })
                        .when().xpath("/order/amount < 100")
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                String xml = exchange.getIn().getBody(String.class);
                                assertTrue(xml.contains("<amount>88</amount>"), xml);

                                int num = med.incrementAndGet();
                                if (num % 100 == 0) {
                                    log.info("Processed {} medium messages", num);
                                    log.debug(xml);
                                }
                            }
                        })
                        .otherwise()
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                String xml = exchange.getIn().getBody(String.class);
                                assertTrue(xml.contains("<amount>123</amount>"), xml);

                                int num = large.incrementAndGet();
                                if (num % 100 == 0) {
                                    log.info("Processed {} large messages", num);
                                    log.debug(xml);
                                }
                            }
                        })
                        .end() // choice
                        .end(); // split
            }
        };
    }

    public void createDataFile(Logger log, int size) throws Exception {
        log.info("Creating data file ...");

        File file = testDirectory.resolve("data.xml").toFile();
        FileOutputStream fos = new FileOutputStream(file, true);
        fos.write("<orders>\n".getBytes());

        for (int i = 0; i < size; i++) {
            fos.write("<order>\n".getBytes());
            fos.write(("  <id>" + i + "</id>\n").getBytes());
            int num = i % 10;
            if (num >= 0 && num <= 3) {
                fos.write("  <amount>3</amount>\n".getBytes());
                fos.write("  <customerId>333</customerId>\n".getBytes());
            } else if (num >= 4 && num <= 5) {
                fos.write("  <amount>44</amount>\n".getBytes());
                fos.write("  <customerId>444</customerId>\n".getBytes());
            } else if (num >= 6 && num <= 8) {
                fos.write("  <amount>88</amount>\n".getBytes());
                fos.write("  <customerId>888</customerId>\n".getBytes());
            } else {
                fos.write("  <amount>123</amount>\n".getBytes());
                fos.write("  <customerId>123123</customerId>\n".getBytes());
            }
            fos.write("  <description>bla bla bla bla bla bla bla bla bla bla bla bla bla bla bla bla</description>\n"
                    .getBytes());
            fos.write("</order>\n".getBytes());
        }

        fos.write("</orders>".getBytes());
        fos.close();

        log.info("Creating data file done.");
    }

}
