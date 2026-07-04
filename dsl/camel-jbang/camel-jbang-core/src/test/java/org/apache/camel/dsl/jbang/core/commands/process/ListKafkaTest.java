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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class ListKafkaTest extends ProcessCommandTestSupport {

    @Test
    void testDisplaysKafkaConsumer() throws Exception {
        writeStatusFile(TEST_PID, buildStatusWithKafkaConsumer("myRoute", "kafka:orders", "my-group", "orders", 0, 42L));

        ListKafka command = new ListKafka(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("myApp"), "Should show integration name");
            assertTrue(output.contains("orders"), "Should show topic name");
            assertTrue(output.contains("my-group"), "Should show consumer group");
        }
    }

    @Test
    void testEmptyOutputWhenNoKafkaSection() throws Exception {
        JsonObject status = buildContextStatus("noKafkaApp", 5);
        writeStatusFile(TEST_PID, status);

        ListKafka command = new ListKafka(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertEquals("", printer.getOutput().trim(), "No kafka section means no output rows");
        }
    }

    @Test
    void testEmptyOutputWhenNoProcesses() throws Exception {
        ListKafka command = new ListKafka(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.empty());

            int exit = command.doCall();

            assertEquals(0, exit);
            assertEquals("", printer.getOutput().trim());
        }
    }

    @Test
    void testJsonOutput() throws Exception {
        writeStatusFile(TEST_PID, buildStatusWithKafkaConsumer("myRoute", "kafka:events", "event-group", "events", 1, 7L));

        ListKafka command = new ListKafka(new CamelJBangMain().withPrinter(printer));
        command.jsonOutput = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.startsWith("["), "JSON output should be an array");
            assertTrue(output.contains("events"), "JSON should contain topic name");
        }
    }

    private static JsonObject buildStatusWithKafkaConsumer(
            String routeId, String uri, String groupId, String topic, int partition, long offset) {
        JsonObject worker = new JsonObject();
        worker.put("threadId", "thread-1");
        worker.put("state", "Running");
        worker.put("groupId", groupId);
        worker.put("lastTopic", topic);
        worker.put("lastPartition", partition);
        worker.put("lastOffset", offset);

        JsonArray workers = new JsonArray();
        workers.add(worker);

        JsonObject consumer = new JsonObject();
        consumer.put("routeId", routeId);
        consumer.put("uri", uri);
        consumer.put("state", "Running");
        consumer.put("workers", workers);

        JsonArray consumers = new JsonArray();
        consumers.add(consumer);

        JsonObject kafka = new JsonObject();
        kafka.put("kafkaConsumers", consumers);

        JsonObject root = buildContextStatus("myApp", 5);
        root.put("kafka", kafka);
        return root;
    }
}
