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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.CamelContext;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.camel.util.CamelContextHelper;

/**
 * Represents an XML &lt;redeliveryPolicy/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "redeliveryPolicy")
@XmlAccessorType(XmlAccessType.FIELD)
public class RedeliveryPolicyType {
    @XmlAttribute()
    private String ref;
    @XmlAttribute
    private Integer maximumRedeliveries;
    @XmlAttribute
    private Long initialRedeliveryDelay;
    @XmlAttribute
    private Double backOffMultiplier;
    @XmlAttribute
    private Boolean useExponentialBackOff;
    @XmlAttribute
    private Double collisionAvoidanceFactor;
    @XmlAttribute
    private Boolean useCollisionAvoidance;
    @XmlAttribute
    private Long maximumRedeliveryDelay;
    @XmlAttribute
    private LoggingLevel retriesExhaustedLogLevel;
    @XmlAttribute
    private LoggingLevel retryAttemptedLogLevel;

    public RedeliveryPolicy createRedeliveryPolicy(CamelContext context, RedeliveryPolicy parentPolicy) {
        if (ref != null) {
            // lookup in registry if ref provided
            return CamelContextHelper.mandatoryLookup(context, ref, RedeliveryPolicy.class);
        }

        RedeliveryPolicy answer = parentPolicy.copy();

        // copy across the properties - if they are set
        if (maximumRedeliveries != null) {
            answer.setMaximumRedeliveries(maximumRedeliveries);
        }
        if (initialRedeliveryDelay != null) {
            answer.setDelay(initialRedeliveryDelay);
        }
        if (retriesExhaustedLogLevel != null) {
            answer.setRetriesExhaustedLogLevel(retriesExhaustedLogLevel);
        }
        if (retryAttemptedLogLevel != null) {
            answer.setRetryAttemptedLogLevel(retryAttemptedLogLevel);
        }
        if (backOffMultiplier != null) {
            answer.setBackOffMultiplier(backOffMultiplier);
        }
        if (useExponentialBackOff != null) {
            answer.setUseExponentialBackOff(useExponentialBackOff);
        }
        if (collisionAvoidanceFactor != null) {
            answer.setCollisionAvoidanceFactor(collisionAvoidanceFactor);
        }
        if (useCollisionAvoidance != null) {
            answer.setUseCollisionAvoidance(useCollisionAvoidance);
        }
        if (maximumRedeliveryDelay != null) {
            answer.setMaximumRedeliveryDelay(maximumRedeliveryDelay);
        }
        return answer;
    }

    public String toString() {
        return "RedeliveryPolicy[maximumRedeliveries: " + maximumRedeliveries + "]";
    }

    // Fluent API
    //-------------------------------------------------------------------------
    public RedeliveryPolicyType backOffMultiplier(double backOffMultiplier) {
        setBackOffMultiplier(backOffMultiplier);
        return this;
    }

    public RedeliveryPolicyType collisionAvoidancePercent(double collisionAvoidancePercent) {
        setCollisionAvoidanceFactor(collisionAvoidancePercent * 0.01d);
        return this;
    }

    public RedeliveryPolicyType collisionAvoidanceFactor(double collisionAvoidanceFactor) {
        setCollisionAvoidanceFactor(collisionAvoidanceFactor);
        return this;
    }

    public RedeliveryPolicyType initialRedeliveryDelay(long initialRedeliveryDelay) {
        setInitialRedeliveryDelay(initialRedeliveryDelay);
        return this;
    }

    public RedeliveryPolicyType retriesExhaustedLogLevel(LoggingLevel retriesExhaustedLogLevel) {
        setRetriesExhaustedLogLevel(retriesExhaustedLogLevel);
        return this;
    }    
    
    public RedeliveryPolicyType retryAttemptedLogLevel(LoggingLevel retryAttemptedLogLevel) {
        setRetryAttemptedLogLevel(retryAttemptedLogLevel);
        return this;
    }
        
    public RedeliveryPolicyType maximumRedeliveries(int maximumRedeliveries) {
        setMaximumRedeliveries(maximumRedeliveries);
        return this;
    }

    public RedeliveryPolicyType useCollisionAvoidance() {
        setUseCollisionAvoidance(Boolean.TRUE);
        return this;
    }

    public RedeliveryPolicyType useExponentialBackOff() {
        setUseExponentialBackOff(Boolean.TRUE);
        return this;
    }

    public RedeliveryPolicyType maximumRedeliveryDelay(long maximumRedeliveryDelay) {
        setMaximumRedeliveryDelay(maximumRedeliveryDelay);
        return this;
    }

    public RedeliveryPolicyType ref(String ref) {
        setRef(ref);
        return this;
    }

    // Properties
    //-------------------------------------------------------------------------

    public Double getBackOffMultiplier() {
        return backOffMultiplier;
    }

    public void setBackOffMultiplier(Double backOffMultiplier) {
        this.backOffMultiplier = backOffMultiplier;
    }

    public Double getCollisionAvoidanceFactor() {
        return collisionAvoidanceFactor;
    }

    public void setCollisionAvoidanceFactor(Double collisionAvoidanceFactor) {
        this.collisionAvoidanceFactor = collisionAvoidanceFactor;
    }

    public Long getInitialRedeliveryDelay() {
        return initialRedeliveryDelay;
    }

    public void setInitialRedeliveryDelay(Long initialRedeliveryDelay) {
        this.initialRedeliveryDelay = initialRedeliveryDelay;
    }

    public Integer getMaximumRedeliveries() {
        return maximumRedeliveries;
    }

    public void setMaximumRedeliveries(Integer maximumRedeliveries) {
        this.maximumRedeliveries = maximumRedeliveries;
    }

    public Boolean getUseCollisionAvoidance() {
        return useCollisionAvoidance;
    }

    public void setUseCollisionAvoidance(Boolean useCollisionAvoidance) {
        this.useCollisionAvoidance = useCollisionAvoidance;
    }

    public Boolean getUseExponentialBackOff() {
        return useExponentialBackOff;
    }

    public void setUseExponentialBackOff(Boolean useExponentialBackOff) {
        this.useExponentialBackOff = useExponentialBackOff;
    }

    public Long getMaximumRedeliveryDelay() {
        return maximumRedeliveryDelay;
    }

    public void setMaximumRedeliveryDelay(Long maximumRedeliveryDelay) {
        this.maximumRedeliveryDelay = maximumRedeliveryDelay;
    }

    public void setRetriesExhaustedLogLevel(LoggingLevel retriesExhaustedLogLevel) {
        this.retriesExhaustedLogLevel = retriesExhaustedLogLevel;
    }

    public LoggingLevel getRetriesExhaustedLogLevel() {
        return retriesExhaustedLogLevel;
    } 

    public void setRetryAttemptedLogLevel(LoggingLevel retryAttemptedLogLevel) {
        this.retryAttemptedLogLevel = retryAttemptedLogLevel;
    }

    public LoggingLevel getRetryAttemptedLogLevel() {
        return retryAttemptedLogLevel;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }
}
