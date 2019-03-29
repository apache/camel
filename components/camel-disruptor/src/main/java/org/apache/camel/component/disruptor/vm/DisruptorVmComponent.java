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
package org.apache.camel.component.disruptor.vm;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.component.disruptor.DisruptorComponent;
import org.apache.camel.component.disruptor.DisruptorReference;
import org.apache.camel.spi.annotations.Component;

/**
 * An implementation of the <a href="http://camel.apache.org/vm.html">VM components</a>
 * for asynchronous SEDA exchanges on a
 * <a href="https://github.com/LMAX-Exchange/disruptor">LMAX Disruptor</a> within the classloader tree containing
 * the camel-disruptor.jar. i.e. to handle communicating across CamelContext instances and possibly across
 * web application contexts, providing that camel-disruptor.jar is on the system classpath.
 */
@Component("disruptor-vm")
public class DisruptorVmComponent extends DisruptorComponent {
    protected static final Map<String, DisruptorReference> DISRUPTORS = new HashMap<>();
    private static final AtomicInteger START_COUNTER = new AtomicInteger();

    @Override
    public Map<String, DisruptorReference> getDisruptors() {
        return DISRUPTORS;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        START_COUNTER.incrementAndGet();
    }

    @Override
    protected void doStop() throws Exception {
        if (START_COUNTER.decrementAndGet() <= 0) {
            // clear queues when no more vm components in use
            getDisruptors().clear();
        }
    }
}
