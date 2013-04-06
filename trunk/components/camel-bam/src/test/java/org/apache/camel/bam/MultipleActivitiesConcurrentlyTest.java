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
package org.apache.camel.bam;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

/**
 * @version 
 */
public class MultipleActivitiesConcurrentlyTest extends MultipleProcessesTest {

    @Override
    @Test
    public void testBam() throws Exception {
        // TODO fixme
        //overdueEndpoint.expectedMessageCount(1);
        overdueEndpoint.expectedMinimumMessageCount(1);
        //overdueEndpoint.message(0).predicate(el("${in.body.correlationKey == '124'}"));

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(1);

        Thread thread = new Thread("B sender") {
            public void run() {
                startLatch.countDown();
                sendBMessages();
                endLatch.countDown();
            }
        };
        thread.start();

        // use a timeout to avoid test hang if something happens
        assertTrue(startLatch.await(30, TimeUnit.SECONDS));

        sendAMessages();

        // use a timeout to avoid test hang if something happens
        assertTrue(endLatch.await(30, TimeUnit.SECONDS));

        overdueEndpoint.assertIsSatisfied();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        errorTimeout = 5;

        super.setUp();
    }
}
