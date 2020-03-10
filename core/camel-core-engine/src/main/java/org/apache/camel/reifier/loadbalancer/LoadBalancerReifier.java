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

    private static final Map<Class<?>, BiFunction<Route, LoadBalancerDefinition, LoadBalancerReifier<? extends LoadBalancerDefinition>>> LOAD_BALANCERS;
    static {
        Map<Class<?>, BiFunction<Route, LoadBalancerDefinition, LoadBalancerReifier<? extends LoadBalancerDefinition>>> map = new HashMap<>();
        map.put(LoadBalancerDefinition.class, LoadBalancerReifier::new);
        map.put(CustomLoadBalancerDefinition.class, CustomLoadBalancerReifier::new);
        map.put(FailoverLoadBalancerDefinition.class, FailoverLoadBalancerReifier::new);
        map.put(RandomLoadBalancerDefinition.class, RandomLoadBalancerReifier::new);
        map.put(RoundRobinLoadBalancerDefinition.class, RoundRobinLoadBalancerReifier::new);
        map.put(StickyLoadBalancerDefinition.class, StickyLoadBalancerReifier::new);
        map.put(TopicLoadBalancerDefinition.class, TopicLoadBalancerReifier::new);
        map.put(WeightedLoadBalancerDefinition.class, WeightedLoadBalancerReifier::new);
        LOAD_BALANCERS = map;
        ReifierStrategy.addReifierClearer(LoadBalancerReifier::clearReifiers);
    }

    protected final T definition;

    public LoadBalancerReifier(Route route, T definition) {
        super(route);
        this.definition = definition;
    }

    public static LoadBalancerReifier<? extends LoadBalancerDefinition> reifier(Route route, LoadBalancerDefinition definition) {
        BiFunction<Route, LoadBalancerDefinition, LoadBalancerReifier<? extends LoadBalancerDefinition>> reifier = LOAD_BALANCERS.get(definition.getClass());
        if (reifier != null) {
            return reifier.apply(route, definition);
        }
        throw new IllegalStateException("Unsupported definition: " + definition);
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
