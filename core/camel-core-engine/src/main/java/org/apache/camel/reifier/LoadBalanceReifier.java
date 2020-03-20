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
package org.apache.camel.reifier;

import org.apache.camel.Channel;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.LoadBalanceDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.loadbalancer.FailoverLoadBalancerDefinition;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.reifier.loadbalancer.LoadBalancerReifier;

public class LoadBalanceReifier extends ProcessorReifier<LoadBalanceDefinition> {

    public LoadBalanceReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (LoadBalanceDefinition)definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        LoadBalancer loadBalancer = LoadBalancerReifier.reifier(route, definition.getLoadBalancerType()).createLoadBalancer();

        // some load balancer can only support a fixed number of outputs
        int max = definition.getLoadBalancerType().getMaximumNumberOfOutputs();
        int size = definition.getOutputs().size();
        if (size > max) {
            throw new IllegalArgumentException("To many outputs configured on " + definition.getLoadBalancerType() + ": " + size + " > " + max);
        }

        for (ProcessorDefinition<?> processorType : definition.getOutputs()) {
            // output must not be another load balancer
            // check for instanceof as the code below as there is
            // compilation errors on earlier versions of JDK6
            // on Windows boxes or with IBM JDKs etc.
            if (LoadBalanceDefinition.class.isInstance(processorType)) {
                throw new IllegalArgumentException("Loadbalancer already configured to: " + definition.getLoadBalancerType() + ". Cannot set it to: " + processorType);
            }
            Processor processor = createProcessor(processorType);
            Channel channel = wrapChannel(processor, processorType);
            loadBalancer.addProcessor(channel);
        }

        Boolean inherit = definition.isInheritErrorHandler();
        if (definition.getLoadBalancerType() instanceof FailoverLoadBalancerDefinition) {
            // special for failover load balancer where you can configure it to
            // not inherit error handler for its children
            // but the load balancer itself should inherit so Camels error
            // handler can react afterwards
            inherit = true;
        }
        Processor target = wrapChannel(loadBalancer, definition, inherit);
        return target;
    }

}
