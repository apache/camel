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
package org.apache.camel.utils.cassandra;

import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.ErrorAwarePolicy;
import com.datastax.driver.core.policies.LatencyAwarePolicy;
import com.datastax.driver.core.policies.LoadBalancingPolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.datastax.driver.core.policies.TokenAwarePolicy;

public class CassandraLoadBalancingPolicies {

    public final String roundRobinPolicy = "RoundRobinPolicy";
    public final String tokenAwarePolicy = "TokenAwarePolicy";
    public final String dcAwareRoundRobinPolicy = "DcAwareRoundRobinPolicy";
    public final String latencyAwarePolicy = "LatencyAwarePolicy";
    public final String errorAwarePolicy = "ErrorAwarePolicy";
    
    public LoadBalancingPolicy getLoadBalancingPolicy(String policy) {
        LoadBalancingPolicy loadBalancingPolicy = new RoundRobinPolicy();
        switch (policy) {
        case roundRobinPolicy:
            loadBalancingPolicy = new RoundRobinPolicy();
            break;
        case tokenAwarePolicy:
            loadBalancingPolicy = new TokenAwarePolicy(new RoundRobinPolicy());
            break;
        case dcAwareRoundRobinPolicy:
            loadBalancingPolicy = DCAwareRoundRobinPolicy.builder().build();
            break;
        case latencyAwarePolicy:
            loadBalancingPolicy = LatencyAwarePolicy.builder(new RoundRobinPolicy()).build();
            break;
        case errorAwarePolicy:
            loadBalancingPolicy = ErrorAwarePolicy.builder(new RoundRobinPolicy()).build();
            break;
        default:
            throw new IllegalArgumentException("Cassandra load balancing policy can be " + roundRobinPolicy + " ," + tokenAwarePolicy 
                   + " ," + dcAwareRoundRobinPolicy);
        }
        return loadBalancingPolicy;
    }
}
