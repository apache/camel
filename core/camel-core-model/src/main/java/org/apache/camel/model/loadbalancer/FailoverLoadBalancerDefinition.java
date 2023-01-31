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
package org.apache.camel.model.loadbalancer;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.LoadBalancerDefinition;
import org.apache.camel.spi.Metadata;

/**
 * In case of failures the exchange will be tried on the next endpoint.
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "failover")
@XmlAccessorType(XmlAccessType.FIELD)
public class FailoverLoadBalancerDefinition extends LoadBalancerDefinition {
    @XmlTransient
    private List<Class<?>> exceptionTypes = new ArrayList<>();

    @XmlElement(name = "exception")
    private List<String> exceptions = new ArrayList<>();
    @XmlAttribute
    private String roundRobin;
    @XmlAttribute
    private String sticky;
    @XmlAttribute
    @Metadata(defaultValue = "-1")
    private String maximumFailoverAttempts;

    public FailoverLoadBalancerDefinition() {
    }

    public List<String> getExceptions() {
        return exceptions;
    }

    /**
     * A list of class names for specific exceptions to monitor. If no exceptions are configured then all exceptions are
     * monitored
     */
    public void setExceptions(List<String> exceptions) {
        this.exceptions = exceptions;
    }

    public List<Class<?>> getExceptionTypes() {
        return exceptionTypes;
    }

    /**
     * A list of specific exceptions to monitor. If no exceptions are configured then all exceptions are monitored
     */
    public void setExceptionTypes(List<Class<?>> exceptionTypes) {
        this.exceptionTypes = exceptionTypes;
    }

    public String getRoundRobin() {
        return roundRobin;
    }

    /**
     * Whether or not the failover load balancer should operate in round robin mode or not. If not, then it will always
     * start from the first endpoint when a new message is to be processed. In other words it restart from the top for
     * every message. If round robin is enabled, then it keeps state and will continue with the next endpoint in a round
     * robin fashion.
     * <p/>
     * You can also enable sticky mode together with round robin, if so then it will pick the last known good endpoint
     * to use when starting the load balancing (instead of using the next when starting).
     */
    public void setRoundRobin(String roundRobin) {
        this.roundRobin = roundRobin;
    }

    public String getSticky() {
        return sticky;
    }

    /**
     * Whether or not the failover load balancer should operate in sticky mode or not. If not, then it will always start
     * from the first endpoint when a new message is to be processed. In other words it restart from the top for every
     * message. If sticky is enabled, then it keeps state and will continue with the last known good endpoint.
     * <p/>
     * You can also enable sticky mode together with round robin, if so then it will pick the last known good endpoint
     * to use when starting the load balancing (instead of using the next when starting).
     */
    public void setSticky(String sticky) {
        this.sticky = sticky;
    }

    public String getMaximumFailoverAttempts() {
        return maximumFailoverAttempts;
    }

    /**
     * A value to indicate after X failover attempts we should exhaust (give up). Use -1 to indicate never give up and
     * continuously try to failover. Use 0 to never failover. And use e.g. 3 to failover at most 3 times before giving
     * up. his option can be used whether or not roundRobin is enabled or not.
     */
    public void setMaximumFailoverAttempts(String maximumFailoverAttempts) {
        this.maximumFailoverAttempts = maximumFailoverAttempts;
    }

    @Override
    public String toString() {
        return "FailoverLoadBalancer";
    }
}
