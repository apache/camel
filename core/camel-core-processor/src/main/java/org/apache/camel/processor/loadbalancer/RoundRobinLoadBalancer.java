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
package org.apache.camel.processor.loadbalancer;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;

/**
 * Implements the round robin load balancing policy
 */
public class RoundRobinLoadBalancer extends QueueLoadBalancer {
    private final AtomicInteger counter = new AtomicInteger(-1);

    @Override
    protected AsyncProcessor chooseProcessor(AsyncProcessor[] processors, Exchange exchange) {
        int size = processors.length;
        int c = counter.updateAndGet(x -> ++x < size ? x : 0);
        return processors[c];
    }

    public int getLastChosenProcessorIndex() {
        return counter.get();
    }

}
