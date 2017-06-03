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
package org.apache.camel.model.loadbalancer;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.LoadBalancerDefinition;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

/**
 * Round robin load balancer
 *
 * The round robin load balancer will use the next endpoint for each message.
 * This load balancer is not meant to work with failover, for that you should use the dedicated failover load balancer.
 */
@Metadata(label = "eip,routing,loadbalance")
@XmlRootElement(name = "roundRobin")
@XmlAccessorType(XmlAccessType.FIELD)
public class RoundRobinLoadBalancerDefinition extends LoadBalancerDefinition {

    public RoundRobinLoadBalancerDefinition() {
    }

    @Override
    protected LoadBalancer createLoadBalancer(RouteContext routeContext) {
        return new org.apache.camel.processor.loadbalancer.RoundRobinLoadBalancer();
    }

    @Override
    public String toString() {
        return "RoundRobinLoadBalancer";
    }

}
