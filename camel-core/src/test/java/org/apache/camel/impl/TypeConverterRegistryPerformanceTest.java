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
package org.apache.camel.impl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.w3c.dom.Document;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.util.StopWatch;

public class TypeConverterRegistryPerformanceTest extends ContextTestSupport {

    private int pool = 100;
    private int inner = 10;
    private int size = 20000;

    private ExecutorService executorService;
    private CountDownLatch latch;

    public void testManual() throws Exception {
        // noop
    }

    public void disbledtestPerformance() throws Exception {
        // force converter to be loaded on startup
        Document dom = context.getTypeConverter().convertTo(Document.class, "<hello>World</hello>");
        assertNotNull(dom);

        StopWatch watch = new StopWatch();

        latch = new CountDownLatch(size);
        executorService = Executors.newFixedThreadPool(pool);

        for (int i = 0; i < size; i++) {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    for (int j = 0; j < inner; j++) {
                        Document dom = context.getTypeConverter().convertTo(Document.class, "<hello>World</hello>");
                        assertNotNull(dom);
                    }
                    latch.countDown();
                }
            });
        }

        assertTrue("Should all work", latch.await(2, TimeUnit.MINUTES));
        log.info("Took " + watch.taken());

        executorService.shutdownNow();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
