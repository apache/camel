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
package org.apache.camel.management.mbean;

import java.util.Iterator;
import java.util.List;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedFailoverLoadBalancerMBean;
import org.apache.camel.model.LoadBalanceDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.loadbalancer.ExceptionFailureStatistics;
import org.apache.camel.processor.loadbalancer.FailOverLoadBalancer;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.ObjectHelper;

@ManagedResource(description = "Managed Failover LoadBalancer")
public class ManagedFailoverLoadBalancer extends ManagedProcessor implements ManagedFailoverLoadBalancerMBean {
    private final FailOverLoadBalancer processor;
    private String exceptions;

    public ManagedFailoverLoadBalancer(CamelContext context, FailOverLoadBalancer processor, LoadBalanceDefinition definition) {
        super(context, processor, definition);
        this.processor = processor;
    }

    @Override
    public LoadBalanceDefinition getDefinition() {
        return (LoadBalanceDefinition) super.getDefinition();
    }

    @Override
    public void reset() {
        super.reset();
        processor.reset();
    }

    @Override
    public Boolean getSupportExtendedInformation() {
        return true;
    }

    @Override
    public Integer getSize() {
        return processor.getProcessors().size();
    }

    @Override
    public Boolean isRoundRobin() {
        return processor.isRoundRobin();
    }

    @Override
    public Boolean isSticky() {
        return processor.isSticky();
    }

    @Override
    public Integer getMaximumFailoverAttempts() {
        return processor.getMaximumFailoverAttempts();
    }

    @Override
    public String getExceptions() {
        if (exceptions != null) {
            return exceptions;
        }

        List<Class<?>> classes = processor.getExceptions();
        if (classes == null || classes.isEmpty()) {
            exceptions = "";
        } else {
            CollectionStringBuffer csb = new CollectionStringBuffer(",");
            for (Class<?> clazz : classes) {
                csb.append(clazz.getCanonicalName());
            }
            exceptions = csb.toString();
        }
        return exceptions;
    }

    @Override
    public String getLastGoodProcessorId() {
        int idx = processor.getLastGoodIndex();
        if (idx != -1) {
            LoadBalanceDefinition def = getDefinition();
            ProcessorDefinition<?> output = def.getOutputs().get(idx);
            if (output != null) {
                return output.getId();
            }
        }
        return null;
    }

    @Override
    public TabularData exceptionStatistics() {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.loadbalancerExceptionsTabularType());

            ExceptionFailureStatistics statistics = processor.getExceptionFailureStatistics();

            Iterator<Class<?>> it = statistics.getExceptions();
            boolean empty = true;
            while (it.hasNext()) {
                empty = false;
                Class<?> exception = it.next();
                String name = ObjectHelper.name(exception);
                long counter = statistics.getFailureCounter(exception);

                CompositeType ct = CamelOpenMBeanTypes.loadbalancerExceptionsCompositeType();
                CompositeData data = new CompositeDataSupport(ct,
                        new String[]{"exception", "failures"},
                        new Object[]{name, counter});
                answer.put(data);
            }
            if (empty) {
                // use Exception as a single general
                String name = ObjectHelper.name(Exception.class);
                long counter = statistics.getFailureCounter(Exception.class);

                CompositeType ct = CamelOpenMBeanTypes.loadbalancerExceptionsCompositeType();
                CompositeData data = new CompositeDataSupport(ct,
                        new String[]{"exception", "failures"},
                        new Object[]{name, counter});
                answer.put(data);
            }

            return answer;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

}
