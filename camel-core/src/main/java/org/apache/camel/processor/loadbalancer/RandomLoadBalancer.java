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
package org.apache.camel.processor.loadbalancer;

import java.util.List;
import java.util.Random;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Implements the random load balancing policy
 *
 * @version 
 */
public class RandomLoadBalancer extends QueueLoadBalancer {

    private static final Random RANDOM = new Random();
    private transient int index;

    protected synchronized Processor chooseProcessor(List<Processor> processors, Exchange exchange) {
        int size = processors.size();
        if (size == 0) {
            return null;
        } else if (size == 1) {
            // there is only 1
            return processors.get(0);
        }

        // pick a random
        index = RANDOM.nextInt(size);
        return processors.get(index);
    }

    public int getLastChosenProcessorIndex() {
        return index;
    }

    public String toString() {
        return "RandomLoadBalancer";
    }
}
