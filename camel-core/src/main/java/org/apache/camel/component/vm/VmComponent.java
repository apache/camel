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
package org.apache.camel.component.vm;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.component.seda.SedaComponent;

/**
 * An implementation of the <a href="http://camel.apache.org/vm.html">VM components</a>
 * for asynchronous SEDA exchanges on a {@link BlockingQueue} within the classloader tree containing
 * the camel-core.jar. i.e. to handle communicating across CamelContext instances and possibly across
 * web application contexts, providing that camel-core.jar is on the system classpath.
 *
 * @version 
 */
public class VmComponent extends SedaComponent {
    protected static final Map<String, BlockingQueue<Exchange>> QUEUES = new HashMap<String, BlockingQueue<Exchange>>();
    private static final AtomicInteger START_COUNTER = new AtomicInteger();

    @Override
    public synchronized BlockingQueue<Exchange> createQueue(String uri, Map<String, Object> parameters) {
        String key = getQueueKey(uri);

        if (QUEUES.containsKey(key)) {
            return QUEUES.get(key);
        }

        // create queue
        BlockingQueue<Exchange> queue;
        Integer size = getAndRemoveParameter(parameters, "size", Integer.class);
        if (size != null && size > 0) {
            queue = new LinkedBlockingQueue<Exchange>(size);
        } else {
            queue = new LinkedBlockingQueue<Exchange>();
        }

        QUEUES.put(key, queue);
        return queue;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        START_COUNTER.incrementAndGet();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (START_COUNTER.decrementAndGet() == 0) {
            synchronized (QUEUES) {
                for (BlockingQueue<Exchange> q : QUEUES.values()) {
                    q.clear();
                }
                QUEUES.clear();
            }
        }
    }
}
