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
package org.apache.camel.component.hystrix.springboot.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertTrue;

/**
 * Testing the hystrix mapping
 */
@RunWith(SpringRunner.class)
@SpringBootApplication
@DirtiesContext
@ContextConfiguration(classes = HystrixMappingAutoConfigurationTest.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HystrixMappingAutoConfigurationTest {

    private static final Logger LOG = LoggerFactory.getLogger(HystrixMappingAutoConfigurationTest.class);

    @Value("${local.server.port}")
    private int serverPort;

    @Test
    public void testHystrixServletMapping() throws Exception {

        CountDownLatch pingFound = new CountDownLatch(1);
        Consumer<String> consumer = s -> {
            if (s != null && s.toLowerCase().contains("ping")) {
                pingFound.countDown();
            }
        };

        URL hystrix = new URL("http://localhost:" + serverPort + "/hystrix.stream");
        try (InputStream stream = consume(hystrix, consumer)) {
            // Hystrix stream is infinite, we stop reading it after the first ping (or timeout)

            assertTrue(pingFound.await(5, TimeUnit.SECONDS));
        }

    }

    private InputStream consume(URL url, final Consumer<String> consumer) throws IOException {
        final InputStream stream = url.openConnection().getInputStream();
        new Thread() {
            @Override
            public void run() {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        consumer.accept(line);
                    }
                } catch (IOException ex) {
                    LOG.error("Consumer thread is terminating", ex);
                }
            }
        }.start();
        return stream;
    }

}
