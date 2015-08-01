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

import java.util.ArrayList;
import java.util.List;

public abstract class WeightedLoadBalancer extends QueueLoadBalancer {
    transient int lastIndex;

    private List<Integer> distributionRatioList = new ArrayList<Integer>();
    private List<DistributionRatio> runtimeRatios = new ArrayList<DistributionRatio>();

    
    public WeightedLoadBalancer(List<Integer> distributionRatios) {
        deepCloneDistributionRatios(distributionRatios);
        loadRuntimeRatios(distributionRatios);
    }
    
    protected void deepCloneDistributionRatios(List<Integer> distributionRatios) {
        for (Integer value : distributionRatios) {
            this.distributionRatioList.add(value);
        }
    }

    public int getLastChosenProcessorIndex() {
        return lastIndex;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (getProcessors().size() != getDistributionRatioList().size()) {
            throw new IllegalArgumentException("Loadbalacing with " + getProcessors().size()
                + " should match number of distributions " + getDistributionRatioList().size());
        }
    }

    protected void loadRuntimeRatios(List<Integer> distributionRatios) {
        int position = 0;
        
        for (Integer value : distributionRatios) {
            runtimeRatios.add(new DistributionRatio(position++, value.intValue()));
        }
    }
    
    protected boolean isRuntimeRatiosZeroed() {
        boolean cleared = true;
        
        for (DistributionRatio runtimeRatio : runtimeRatios) {
            if (runtimeRatio.getRuntimeWeight() > 0) {
                cleared = false;
            }
        }        
        return cleared; 
    }
    
    protected void resetRuntimeRatios() {
        for (DistributionRatio runtimeRatio : runtimeRatios) {
            runtimeRatio.setRuntimeWeight(runtimeRatio.getDistributionWeight());
        }
    }

    public List<Integer> getDistributionRatioList() {
        return distributionRatioList;
    }

    public void setDistributionRatioList(List<Integer> distributionRatioList) {
        this.distributionRatioList = distributionRatioList;
    }

    public List<DistributionRatio> getRuntimeRatios() {
        return runtimeRatios;
    }

    public void setRuntimeRatios(ArrayList<DistributionRatio> runtimeRatios) {
        this.runtimeRatios = runtimeRatios;
    }    
    
}
