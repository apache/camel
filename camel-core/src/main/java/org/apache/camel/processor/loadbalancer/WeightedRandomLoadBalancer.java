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

public class WeightedRandomLoadBalancer extends WeightedLoadBalancer {
    private int randomCounter;
    
    public WeightedRandomLoadBalancer(List<Integer> distributionRatioList) {
        super(distributionRatioList);
    }
    
    /* (non-Javadoc)
     * @see org.apache.camel.processor.loadbalancer.QueueLoadBalancer#chooseProcessor(java.util.List, org.apache.camel.Exchange)
     */
    @Override
    protected Processor chooseProcessor(List<Processor> processors,
            Exchange exchange) {
        
        normalizeDistributionListAgainstProcessors(processors);
        
        boolean found = false;
        
        while (!found) {
            if (getRuntimeRatios().isEmpty())  {
                loadRuntimeRatios(getDistributionRatioList());
            }
            
            randomCounter = 0;
            if (getRuntimeRatios().size() > 0) {
                randomCounter = new Random().nextInt(getRuntimeRatios().size());
            } 
                
            if (getRuntimeRatios().get(randomCounter).getRuntimeWeight() > 0) {
                getRuntimeRatios().get(randomCounter).setRuntimeWeight((getRuntimeRatios().get(randomCounter).getRuntimeWeight()) - 1);
                found = true;
                break;
            } else {
                getRuntimeRatios().remove(randomCounter);
            }
        }

        return processors.get(getRuntimeRatios().get(randomCounter).getProcessorPosition());
    }
    
}
