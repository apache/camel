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
package org.apache.camel.component.mybatis;

import java.util.ArrayDeque;
import java.util.Queue;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultExchange;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;

public class MyBatisConsumerIsolatedTest extends Assert {

    @Test
    public void shouldRespectBatchSize() throws Exception {
        // Given
        int batchSize = 5;
        MyBatisConsumer consumer = new MyBatisConsumer(mock(MyBatisEndpoint.class), mock(Processor.class));
        consumer.setMaxMessagesPerPoll(batchSize);

        Queue<Object> emptyMessageQueue = new ArrayDeque<>();
        for (int i = 0; i < 10; i++) {
            MyBatisConsumer.DataHolder dataHolder = new MyBatisConsumer.DataHolder();
            dataHolder.exchange = new DefaultExchange(mock(CamelContext.class));
            emptyMessageQueue.add(dataHolder);
        }

        // When
        int processedMessages = consumer.processBatch(emptyMessageQueue);

        // Then
        assertEquals(batchSize, processedMessages);
    }

}
