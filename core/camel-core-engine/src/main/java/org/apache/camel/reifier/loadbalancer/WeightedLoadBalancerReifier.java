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
package org.apache.camel.reifier.loadbalancer;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.LoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.WeightedLoadBalancerDefinition;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.processor.loadbalancer.WeightedLoadBalancer;
import org.apache.camel.processor.loadbalancer.WeightedRandomLoadBalancer;
import org.apache.camel.processor.loadbalancer.WeightedRoundRobinLoadBalancer;

public class WeightedLoadBalancerReifier extends LoadBalancerReifier<WeightedLoadBalancerDefinition> {

    public WeightedLoadBalancerReifier(Route route, LoadBalancerDefinition definition) {
        super(route, (WeightedLoadBalancerDefinition)definition);
    }

    @Override
    public LoadBalancer createLoadBalancer() {
        WeightedLoadBalancer loadBalancer;
        List<Integer> distributionRatioList = new ArrayList<>();

        try {
            String[] ratios = definition.getDistributionRatio().split(definition.getDistributionRatioDelimiter());
            for (String ratio : ratios) {
                distributionRatioList.add(parseInt(ratio.trim()));
            }

            boolean isRoundRobin = parseBoolean(definition.getRoundRobin(), false);
            if (isRoundRobin) {
                loadBalancer = new WeightedRoundRobinLoadBalancer(distributionRatioList);
            } else {
                loadBalancer = new WeightedRandomLoadBalancer(distributionRatioList);
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }

        return loadBalancer;
    }

}
