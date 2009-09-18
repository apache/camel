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

package org.apache.camel.web.util;

import java.util.List;

import org.apache.camel.model.LoadBalanceDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.loadbalancer.FailOverLoadBalancer;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.processor.loadbalancer.RandomLoadBalancer;
import org.apache.camel.processor.loadbalancer.RoundRobinLoadBalancer;
import org.apache.camel.processor.loadbalancer.StickyLoadBalancer;
import org.apache.camel.processor.loadbalancer.TopicLoadBalancer;

/**
 *
 */
public final class LoadBalanceDefinitionRenderer {
    private LoadBalanceDefinitionRenderer() {
        // Utility class, no public or protected default constructor
    }    

    public static void render(StringBuilder buffer, ProcessorDefinition processor) {
        LoadBalanceDefinition loadB = (LoadBalanceDefinition)processor;
        // buffer.append(".").append(output.getShortName()).append("()");
        buffer.append(".").append("loadBalance").append("()");

        LoadBalancer lb = loadB.getLoadBalancerType().getLoadBalancer(null);
        if (lb instanceof FailOverLoadBalancer) {
            buffer.append(".failover(");
            List<Class> exceptions = ((FailOverLoadBalancer)lb).getExceptions();
            for (Class excep : exceptions) {
                buffer.append(excep.getSimpleName()).append(".class");
                if (excep != exceptions.get(exceptions.size() - 1)) {
                    buffer.append(", ");
                }
            }
            buffer.append(")");
        } else if (lb instanceof RandomLoadBalancer) {
            buffer.append(".random()");
        } else if (lb instanceof RoundRobinLoadBalancer) {
            buffer.append(".roundRobin()");
        } else if (lb instanceof StickyLoadBalancer) {
            buffer.append(".sticky(");
            ExpressionRenderer.renderExpression(buffer, ((StickyLoadBalancer)lb).getCorrelationExpression().toString());
            buffer.append(")");
        } else if (lb instanceof TopicLoadBalancer) {
            buffer.append(".topic()");
        }

        List<ProcessorDefinition> branches = loadB.getOutputs();
        for (ProcessorDefinition branch : branches) {
            ProcessorDefinitionRenderer.render(buffer, branch);
        }
    }
}
