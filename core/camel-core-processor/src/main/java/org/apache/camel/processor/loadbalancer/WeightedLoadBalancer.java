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

public abstract class WeightedLoadBalancer extends QueueLoadBalancer {
    protected final List<DistributionRatio> ratios;
    protected final int distributionRatioSum;
    protected int runtimeRatioSum;

    transient int lastIndex = -1;

    public WeightedLoadBalancer(List<Integer> distributionRatios) {
        this.ratios = distributionRatios.stream()
                .map(DistributionRatio::new)
                .toList();
        this.distributionRatioSum = ratios.stream()
                .mapToInt(DistributionRatio::getDistributionWeight).sum();
        this.runtimeRatioSum = distributionRatioSum;
    }

    public int getLastChosenProcessorIndex() {
        return lastIndex;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (getProcessors().size() != ratios.size()) {
            throw new IllegalArgumentException(
                    "Loadbalacing with " + getProcessors().size()
                                               + " should match number of distributions " + ratios.size());
        }
    }

    protected void decrementSum() {
        if (--runtimeRatioSum == 0) {
            // every processor is exhausted, reload for a new distribution round
            reset();
        }
    }

    protected void reset() {
        for (DistributionRatio ratio : ratios) {
            ratio.reset();
        }
        runtimeRatioSum = distributionRatioSum;
    }

    public List<DistributionRatio> getRatios() {
        return ratios;
    }

}
