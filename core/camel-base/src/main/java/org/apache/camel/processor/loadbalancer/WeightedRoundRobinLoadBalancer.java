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

import java.util.List;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;

public class WeightedRoundRobinLoadBalancer extends WeightedLoadBalancer {

    int counter = -1;

    public WeightedRoundRobinLoadBalancer(List<Integer> distributionRatios) {
        super(distributionRatios);
    }
    
    @Override
    protected synchronized AsyncProcessor chooseProcessor(AsyncProcessor[] processors, Exchange exchange) {
        int counter = this.counter;
        List<DistributionRatio> ratios = getRatios();
        while (true) {
            if (++counter >= ratios.size()) {
                counter = 0;
            }
            DistributionRatio ratio = ratios.get(counter);
            if (ratio.decrement()) {
                this.counter = lastIndex = counter;
                decrementSum();
                return processors[counter];
            }
        }
    }

    @Override
    protected void reset() {
        super.reset();
        counter = -1;
    }
}
