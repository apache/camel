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
package org.apache.camel.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.camel.Exchange;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DefaultProducerTemplateNonBlockingAsyncTest extends DefaultProducerTemplateAsyncTest {
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        template.stop();
        template.setThreadedAsyncMode(false);
        template.start();
    }

    @Test
    public void testRunningInSameThread() throws ExecutionException, InterruptedException {
        Thread originalThread = Thread.currentThread();
        CompletableFuture<Exchange> future = template.asyncSend("direct:echo", e -> {
            Assert.assertSame(originalThread, Thread.currentThread());
            e.getIn().setBody("Hi");
        });
        Assert.assertEquals("HiHi", template.extractFutureBody(future, String.class));
    }
}
