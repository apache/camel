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
package org.apache.camel.component.zookeeper.operations;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.data.Stat;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FutureEventDrivenOperationTest {
    private String data = "Event Received";
    private Stat statistics = new Stat();

    @Test
    public void shouldWaitForEvents() throws Exception {
        final FutureEventDrivenOperation<String> future = new FutureEventDrivenOperation<String>(null, "somepath", EventType.NodeCreated) {

            @Override
            protected void installWatch() {
            }

            @Override
            public OperationResult<String> getResult() {
                return new OperationResult<>(data, statistics);
            }
        };

        WatchedEvent event = new WatchedEvent(EventType.NodeCreated, null, "somepath");
        fireEventIn(future, event, 100);
        assertEquals(data, future.get().getResult());
        assertEquals(statistics, future.get().getStatistics());
        assertEquals(event, future.getWatchedEvent());
    }

    private void fireEventIn(final FutureEventDrivenOperation<String> future, final WatchedEvent event, final int millisecondsTillFire) {
        new Thread(new Runnable() {

            public void run() {
                try {
                    Thread.sleep(millisecondsTillFire);
                    future.process(event);
                } catch (InterruptedException e) {
                }
            }
        }).start();
    }
}
