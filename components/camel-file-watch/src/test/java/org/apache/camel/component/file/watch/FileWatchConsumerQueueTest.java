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
package org.apache.camel.component.file.watch;

import java.nio.file.Path;

import org.apache.camel.component.file.watch.constants.FileEvent;
import org.apache.camel.component.file.watch.constants.FileEventEnum;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The queue between the watch service and the consumer is bounded when the {@code queueSize} option is set, so an event
 * can fail to be queued. That has to be reported rather than discarded silently.
 */
public class FileWatchConsumerQueueTest {

    @TempDir
    Path folder;

    @Test
    public void shouldReportAnEventDroppedByAFullQueue() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            FileWatchEndpoint endpoint = context.getEndpoint(
                    "file-watch:" + folder.toAbsolutePath() + "?queueSize=1", FileWatchEndpoint.class);
            FileWatchConsumer consumer = (FileWatchConsumer) endpoint.createConsumer(exchange -> {
            });

            FileEvent first = new FileEvent(FileEventEnum.CREATE, folder.resolve("first.txt"));
            FileEvent second = new FileEvent(FileEventEnum.CREATE, folder.resolve("second.txt"));
            FileEvent third = new FileEvent(FileEventEnum.CREATE, folder.resolve("third.txt"));

            assertTrue(consumer.offerEvent(first), "the first event fits in a queue of size 1");
            assertFalse(consumer.offerEvent(second), "the second event is dropped, and that has to be reported");
            // the warning is only logged for the first drop, but every drop still has to be reported to the caller
            assertFalse(consumer.offerEvent(third), "a later drop is reported too, even though it only logs at DEBUG");
        }
    }

}
