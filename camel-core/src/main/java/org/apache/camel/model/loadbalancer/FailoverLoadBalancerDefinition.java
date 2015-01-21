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

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.LoadBalancerDefinition;
import org.apache.camel.processor.loadbalancer.FailOverLoadBalancer;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.spi.Label;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

/**
 * Failover load balancer
 *
 * The failover load balancer is capable of trying the next processor in case an Exchange failed with an exception during processing.
 * You can constrain the failover to activate only when one exception of a list you specify occurs.
 * If you do not specify a list any exception will cause fail over to occur.
 * This balancer uses the same strategy for matching exceptions as the Exception Clause does for the onException.
 */
@Label("eip,routing")
@XmlRootElement(name = "failover")
@XmlAccessorType(XmlAccessType.FIELD)
public class FailoverLoadBalancerDefinition extends LoadBalancerDefinition {
    @XmlElement(name = "exception")
    private List<String> exceptions = new ArrayList<String>();
    @XmlAttribute
    private Boolean roundRobin;
    @XmlAttribute @Metadata(defaultValue = "-1")
    private Integer maximumFailoverAttempts;

    public FailoverLoadBalancerDefinition() {
    }

    @Override
    protected LoadBalancer createLoadBalancer(RouteContext routeContext) {
        FailOverLoadBalancer answer;

        if (!exceptions.isEmpty()) {
            List<Class<?>> classes = new ArrayList<Class<?>>();
            for (String name : exceptions) {
                Class<?> type = routeContext.getCamelContext().getClassResolver().resolveClass(name);
                if (type == null) {
                    throw new IllegalArgumentException("Cannot find class: " + name + " in the classpath");
                }
                classes.add(type);
            }
            answer = new FailOverLoadBalancer(classes);
        } else {
            answer = new FailOverLoadBalancer();
        }

        if (getMaximumFailoverAttempts() != null) {
            answer.setMaximumFailoverAttempts(getMaximumFailoverAttempts());
        }
        if (roundRobin != null) {
            answer.setRoundRobin(roundRobin);
        }

        return answer;
    }

    public List<String> getExceptions() {
        return exceptions;
    }

    /**
     * A list of class names for specific exceptions to monitor.
     * If no exceptions is configured then all exceptions is monitored
     */
    public void setExceptions(List<String> exceptions) {
        this.exceptions = exceptions;
    }

    public Boolean getRoundRobin() {
        return roundRobin;
    }

    /**
     * Whether or not the failover load balancer should operate in round robin mode or not.
     * If not, then it will always start from the first endpoint when a new message is to be processed.
     * In other words it restart from the top for every message.
     * If round robin is enabled, then it keeps state and will continue with the next endpoint in a round robin fashion.
     * When using round robin it will not stick to last known good endpoint, it will always pick the next endpoint to use.
     */
    public void setRoundRobin(Boolean roundRobin) {
        this.roundRobin = roundRobin;
    }

    public Integer getMaximumFailoverAttempts() {
        return maximumFailoverAttempts;
    }

    /**
     * A value to indicate after X failover attempts we should exhaust (give up).
     * Use -1 to indicate never give up and continuously try to failover. Use 0 to never failover.
     * And use e.g. 3 to failover at most 3 times before giving up.
     * his option can be used whether or not roundRobin is enabled or not.
     */
    public void setMaximumFailoverAttempts(Integer maximumFailoverAttempts) {
        this.maximumFailoverAttempts = maximumFailoverAttempts;
    }

    @Override
    public String toString() {
        return "FailoverLoadBalancer";
    }
}
