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
    private final Random rnd = new Random();
    private final int distributionRatioSum;
    private int runtimeRatioSum;

    public WeightedRandomLoadBalancer(List<Integer> distributionRatioList) {
        super(distributionRatioList);
        int sum = 0;
        for (Integer distributionRatio : distributionRatioList) {
            sum += distributionRatio;
        }
        distributionRatioSum = sum;
        runtimeRatioSum = distributionRatioSum;
    }
    
    @Override
    protected Processor chooseProcessor(List<Processor> processors, Exchange exchange) {        
        int index = selectProcessIndex();
        lastIndex = index;
        return processors.get(index);
    }
    
    public int selectProcessIndex() {
        if (runtimeRatioSum == 0) { // every processor is exhausted, reload for a new distribution round
            for (DistributionRatio distributionRatio : getRuntimeRatios()) {
                int weight = distributionRatio.getDistributionWeight();
                distributionRatio.setRuntimeWeight(weight);
            }
            runtimeRatioSum = distributionRatioSum;
        }

        DistributionRatio selected = null;
        int randomWeight = rnd.nextInt(runtimeRatioSum);
        int choiceWeight = 0;
        for (DistributionRatio distributionRatio : getRuntimeRatios()) {
            choiceWeight += distributionRatio.getRuntimeWeight();
            if (randomWeight < choiceWeight) {
                selected = distributionRatio;
                break;
            }
        }
        
        selected.setRuntimeWeight(selected.getRuntimeWeight() - 1);
        runtimeRatioSum--;

        return selected.getProcessorPosition();
    }
}
