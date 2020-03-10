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
import org.apache.camel.model.LoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.FailoverLoadBalancerDefinition;
import org.apache.camel.processor.loadbalancer.FailOverLoadBalancer;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.util.ObjectHelper;

public class FailoverLoadBalancerReifier extends LoadBalancerReifier<FailoverLoadBalancerDefinition> {

    public FailoverLoadBalancerReifier(Route route, LoadBalancerDefinition definition) {
        super(route, (FailoverLoadBalancerDefinition)definition);
    }

    @Override
    public LoadBalancer createLoadBalancer() {
        FailOverLoadBalancer answer;

        List<Class<?>> classes = new ArrayList<>();
        if (!definition.getExceptionTypes().isEmpty()) {
            classes.addAll(definition.getExceptionTypes());
        } else if (!definition.getExceptions().isEmpty()) {
            for (String name : definition.getExceptions()) {
                Class<?> type = camelContext.getClassResolver().resolveClass(name);
                if (type == null) {
                    throw new IllegalArgumentException("Cannot find class: " + name + " in the classpath");
                }
                if (!ObjectHelper.isAssignableFrom(Throwable.class, type)) {
                    throw new IllegalArgumentException("Class is not an instance of Throwable: " + type);
                }
                classes.add(type);
            }
        }
        if (classes.isEmpty()) {
            answer = new FailOverLoadBalancer();
        } else {
            answer = new FailOverLoadBalancer(classes);
        }

        if (definition.getMaximumFailoverAttempts() != null) {
            answer.setMaximumFailoverAttempts(parseInt(definition.getMaximumFailoverAttempts()));
        }
        if (definition.getRoundRobin() != null) {
            answer.setRoundRobin(parseBoolean(definition.getRoundRobin(), false));
        }
        if (definition.getSticky() != null) {
            answer.setSticky(parseBoolean(definition.getSticky(), false));
        }

        return answer;
    }

}
