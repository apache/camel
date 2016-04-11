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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class WeightedRoundRobinLoadBalancer extends WeightedLoadBalancer {
    private int counter;
    
    public WeightedRoundRobinLoadBalancer(List<Integer> distributionRatios) {
        super(distributionRatios);
    }
    
    @Override
    protected synchronized Processor chooseProcessor(List<Processor> processors, Exchange exchange) {
        if (isRuntimeRatiosZeroed())  {
            resetRuntimeRatios();
            counter = 0;
        }
        
        boolean found = false;
        while (!found) {
            if (counter >= getRuntimeRatios().size()) {
                counter = 0;
            }
            
            if (getRuntimeRatios().get(counter).getRuntimeWeight() > 0) {
                getRuntimeRatios().get(counter).setRuntimeWeight((getRuntimeRatios().get(counter).getRuntimeWeight()) - 1);
                found = true;
            } else {
                counter++;
            }
        }

        lastIndex = counter;
       
        return processors.get(counter++);
    }
    
}
