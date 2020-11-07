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

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.camel.Route;
import org.apache.camel.model.LoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.CustomLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.FailoverLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.RandomLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.RoundRobinLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.StickyLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.TopicLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.WeightedLoadBalancerDefinition;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.reifier.AbstractReifier;
import org.apache.camel.spi.ReifierStrategy;
import org.apache.camel.util.StringHelper;

public class LoadBalancerReifier<T extends LoadBalancerDefinition> extends AbstractReifier {

    // for custom reifiers
    private static final Map<Class<?>, BiFunction<Route, LoadBalancerDefinition, LoadBalancerReifier<? extends LoadBalancerDefinition>>> LOAD_BALANCERS
            = new HashMap<>(0);

    protected final T definition;

    public LoadBalancerReifier(Route route, T definition) {
        super(route);
        this.definition = definition;
    }

    public static void registerReifier(
            Class<?> processorClass,
            BiFunction<Route, LoadBalancerDefinition, LoadBalancerReifier<? extends LoadBalancerDefinition>> creator) {
        if (LOAD_BALANCERS.isEmpty()) {
            ReifierStrategy.addReifierClearer(LoadBalancerReifier::clearReifiers);
        }
        LOAD_BALANCERS.put(processorClass, creator);
    }

    public static LoadBalancerReifier<? extends LoadBalancerDefinition> reifier(
            Route route, LoadBalancerDefinition definition) {

        LoadBalancerReifier<? extends LoadBalancerDefinition> answer = null;
        if (!LOAD_BALANCERS.isEmpty()) {
            // custom take precedence
            BiFunction<Route, LoadBalancerDefinition, LoadBalancerReifier<? extends LoadBalancerDefinition>> reifier
                    = LOAD_BALANCERS.get(definition.getClass());
            if (reifier != null) {
                answer = reifier.apply(route, definition);
            }
        }
        if (answer == null) {
            answer = coreReifier(route, definition);
        }
        if (answer == null) {
            throw new IllegalStateException("Unsupported definition: " + definition);
        }
        return answer;
    }

    private static LoadBalancerReifier<? extends LoadBalancerDefinition> coreReifier(
            Route route, LoadBalancerDefinition definition) {
        if (definition instanceof CustomLoadBalancerDefinition) {
            return new CustomLoadBalancerReifier(route, definition);
        } else if (definition instanceof FailoverLoadBalancerDefinition) {
            return new FailoverLoadBalancerReifier(route, definition);
        } else if (definition instanceof RandomLoadBalancerDefinition) {
            return new RandomLoadBalancerReifier(route, definition);
        } else if (definition instanceof RoundRobinLoadBalancerDefinition) {
            return new RoundRobinLoadBalancerReifier(route, definition);
        } else if (definition instanceof StickyLoadBalancerDefinition) {
            return new StickyLoadBalancerReifier(route, definition);
        } else if (definition instanceof TopicLoadBalancerDefinition) {
            return new TopicLoadBalancerReifier(route, definition);
        } else if (definition instanceof WeightedLoadBalancerDefinition) {
            return new WeightedLoadBalancerReifier(route, definition);
        }
        return null;
    }

    public static void clearReifiers() {
        LOAD_BALANCERS.clear();
    }

    /**
     * Factory method to create the load balancer from the loadBalancerTypeName
     */
    public LoadBalancer createLoadBalancer() {
        String loadBalancerTypeName = definition.getLoadBalancerTypeName();
        StringHelper.notEmpty(loadBalancerTypeName, "loadBalancerTypeName", this);

        LoadBalancer answer = null;
        if (loadBalancerTypeName != null) {
            Class<?> type = camelContext.getClassResolver().resolveClass(loadBalancerTypeName, LoadBalancer.class);
            if (type == null) {
                throw new IllegalArgumentException("Cannot find class: " + loadBalancerTypeName + " in the classpath");
            }
            answer = (LoadBalancer) camelContext.getInjector().newInstance(type, false);
        }

        return answer;
    }

}
