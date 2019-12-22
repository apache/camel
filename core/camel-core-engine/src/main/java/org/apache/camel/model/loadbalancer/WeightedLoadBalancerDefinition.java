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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.LoadBalancerDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Weighted load balancer The weighted load balancing policy allows you to
 * specify a processing load distribution ratio for each server with respect to
 * others. In addition to the weight, endpoint selection is then further refined
 * using random distribution based on weight.
 */
@Metadata(label = "eip,routing,loadbalance")
@XmlRootElement(name = "weighted")
@XmlAccessorType(XmlAccessType.FIELD)
public class WeightedLoadBalancerDefinition extends LoadBalancerDefinition {
    @XmlAttribute
    private String roundRobin;
    @XmlAttribute(required = true)
    private String distributionRatio;
    @XmlAttribute
    @Metadata(defaultValue = ",")
    private String distributionRatioDelimiter;

    public WeightedLoadBalancerDefinition() {
    }

    public String getRoundRobin() {
        return roundRobin;
    }

    /**
     * To enable round robin mode. By default the weighted distribution mode is
     * used.
     * <p/>
     * The default value is false.
     */
    public void setRoundRobin(String roundRobin) {
        this.roundRobin = roundRobin;
    }

    public String getDistributionRatio() {
        return distributionRatio;
    }

    /**
     * The distribution ratio is a delimited String consisting on integer
     * weights separated by delimiters for example "2,3,5". The
     * distributionRatio must match the number of endpoints and/or processors
     * specified in the load balancer list.
     */
    public void setDistributionRatio(String distributionRatio) {
        this.distributionRatio = distributionRatio;
    }

    public String getDistributionRatioDelimiter() {
        return distributionRatioDelimiter == null ? "," : distributionRatioDelimiter;
    }

    /**
     * Delimiter used to specify the distribution ratio.
     * <p/>
     * The default value is ,
     */
    public void setDistributionRatioDelimiter(String distributionRatioDelimiter) {
        this.distributionRatioDelimiter = distributionRatioDelimiter;
    }

    @Override
    public String toString() {
        if (roundRobin == null || Boolean.FALSE.toString().equals(roundRobin)) {
            return "WeightedRandomLoadBalancer[" + distributionRatio + "]";
        } else if (Boolean.TRUE.toString().equals(roundRobin)) {
            return "WeightedRoundRobinLoadBalancer[" + distributionRatio + "]";
        } else {
            return "WeightedLoadBalancer[roundRobin=" + roundRobin + "," + distributionRatio + "]";
        }
    }
}
