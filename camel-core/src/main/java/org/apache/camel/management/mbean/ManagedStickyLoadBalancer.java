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
package org.apache.camel.management.mbean;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedStickyLoadBalancerMBean;
import org.apache.camel.model.LoadBalanceDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.loadbalancer.StickyLoadBalancerDefinition;
import org.apache.camel.processor.loadbalancer.StickyLoadBalancer;

/**
 * @version 
 */
@ManagedResource(description = "Managed Sticky LoadBalancer")
public class ManagedStickyLoadBalancer extends ManagedProcessor implements ManagedStickyLoadBalancerMBean {
    private final StickyLoadBalancer processor;

    public ManagedStickyLoadBalancer(CamelContext context, StickyLoadBalancer processor, LoadBalanceDefinition definition) {
        super(context, processor, definition);
        this.processor = processor;
    }

    @Override
    public LoadBalanceDefinition getDefinition() {
        return (LoadBalanceDefinition) super.getDefinition();
    }

    @Override
    public String getExpressionLanguage() {
        StickyLoadBalancerDefinition sticky = (StickyLoadBalancerDefinition) getDefinition().getLoadBalancerType();
        return sticky.getCorrelationExpression().getExpressionType().getLanguage();
    }

    @Override
    public String getExpression() {
        StickyLoadBalancerDefinition sticky = (StickyLoadBalancerDefinition) getDefinition().getLoadBalancerType();
        return sticky.getCorrelationExpression().getExpressionType().getExpression();
    }

    @Override
    public Integer getSize() {
        return processor.getProcessors().size();
    }

    @Override
    public String getLastChosenProcessorId() {
        int idx = processor.getLastChosenProcessorIndex();
        if (idx != -1) {
            LoadBalanceDefinition def = getDefinition();
            ProcessorDefinition<?> output = def.getOutputs().get(idx);
            if (output != null) {
                return output.getId();
            }
        }
        return null;
    }

}
