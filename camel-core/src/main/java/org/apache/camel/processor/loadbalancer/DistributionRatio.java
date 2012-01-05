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

public class DistributionRatio {
    private int processorPosition;
    private int distributionWeight;
    private int runtimeWeight;
    
    public DistributionRatio(int processorPosition, int distributionWeight) {
        this(processorPosition, distributionWeight, distributionWeight);
    }

    public DistributionRatio(int processorPosition, int distributionWeight, int runtimeWeight) {
        this.processorPosition = processorPosition;
        this.distributionWeight = distributionWeight;
        this.runtimeWeight = runtimeWeight;
    }
    
    public int getProcessorPosition() {
        return processorPosition;
    }

    public void setProcessorPosition(int processorPosition) {
        this.processorPosition = processorPosition;
    }

    public int getDistributionWeight() {
        return distributionWeight;
    }

    public void setDistributionWeight(int distributionWeight) {
        this.distributionWeight = distributionWeight;
    }

    public int getRuntimeWeight() {
        return runtimeWeight;
    }

    public void setRuntimeWeight(int runtimeWeight) {
        this.runtimeWeight = runtimeWeight;
    }
    
}
