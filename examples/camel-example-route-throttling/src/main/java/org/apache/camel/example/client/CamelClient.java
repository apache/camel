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
package org.apache.camel.example.client;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.util.IOHelper;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Client that uses the {@link ProducerTemplate} to easily exchange messages with the Server.
 * <p/>
 * Requires that the JMS broker is running, as well as CamelServer
 */
public final class CamelClient {

    private static final int SIZE = 10000;
    private static final int POOL = 100;

    private CamelClient() {
        // Helper class
    }

    public static void main(final String[] args) throws Exception {
        System.out.println("Notice this client requires that the CamelServer is already running!");

        AbstractApplicationContext context = new ClassPathXmlApplicationContext("camel-client.xml");

        // get the camel template for Spring template style sending of messages (= producer)
        final ProducerTemplate producer = context.getBean("camelTemplate", ProducerTemplate.class);

        // now send a lot of messages
        System.out.println("Sending ...");

        final CountDownLatch latch = new CountDownLatch(POOL);

        ExecutorService executors = Executors.newFixedThreadPool(POOL);
        for (int i = 0; i < POOL; i++) {
            final int idx = i;
            executors.execute(new Runnable() {
                public void run() {
                    try {
                        for (int j = 0; j < SIZE / POOL; j++) {
                            producer.sendBody("jms:queue:inbox", "Message " + idx * j + j);
                        }
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        latch.await(300, TimeUnit.SECONDS);
        System.out.println("... Send " + SIZE + " message to JMS broker");
        executors.shutdownNow();

        // we're done so let's properly close the application context
        IOHelper.close(context);
    }

}
