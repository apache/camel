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

import org.apache.camel.processor.aggregate.OptimisticLockRetryPolicy;
import org.apache.camel.spi.Metadata;

/**
 * To configure optimistic locking
 *
 * @version
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "optimisticLockRetryPolicy")
@XmlAccessorType(XmlAccessType.FIELD)
public class OptimisticLockRetryPolicyDefinition {
    @XmlAttribute
    private Integer maximumRetries;
    @XmlAttribute @Metadata(defaultValue = "50")
    private Long retryDelay;
    @XmlAttribute @Metadata(defaultValue = "1000")
    private Long maximumRetryDelay;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean exponentialBackOff;
    @XmlAttribute
    private Boolean randomBackOff;

    public OptimisticLockRetryPolicyDefinition() {
    }

    public OptimisticLockRetryPolicy createOptimisticLockRetryPolicy() {
        OptimisticLockRetryPolicy policy = new OptimisticLockRetryPolicy();
        if (maximumRetries != null) {
            policy.setMaximumRetries(maximumRetries);
        }
        if (retryDelay != null) {
            policy.setRetryDelay(retryDelay);
        }
        if (maximumRetryDelay != null) {
            policy.setMaximumRetryDelay(maximumRetryDelay);
        }
        if (exponentialBackOff != null) {
            policy.setExponentialBackOff(exponentialBackOff);
        }
        if (randomBackOff != null) {
            policy.setRandomBackOff(randomBackOff);
        }
        return policy;
    }

    /**
     * Sets the maximum number of retries
     */
    public OptimisticLockRetryPolicyDefinition maximumRetries(int maximumRetries) {
        setMaximumRetries(maximumRetries);
        return this;
    }

    public Integer getMaximumRetries() {
        return maximumRetries;
    }

    public void setMaximumRetries(Integer maximumRetries) {
        this.maximumRetries = maximumRetries;
    }

    /**
     * Sets the delay in millis between retries
     */
    public OptimisticLockRetryPolicyDefinition retryDelay(long retryDelay) {
        setRetryDelay(retryDelay);
        return this;
    }

    public Long getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(Long retryDelay) {
        this.retryDelay = retryDelay;
    }

    /**
     * Sets the upper value of retry in millis between retries, when using exponential or random backoff
     */
    public OptimisticLockRetryPolicyDefinition maximumRetryDelay(long maximumRetryDelay) {
        setMaximumRetryDelay(maximumRetryDelay);
        return this;
    }

    public Long getMaximumRetryDelay() {
        return maximumRetryDelay;
    }

    public void setMaximumRetryDelay(Long maximumRetryDelay) {
        this.maximumRetryDelay = maximumRetryDelay;
    }

    /**
     * Enable exponential backoff
     */
    public OptimisticLockRetryPolicyDefinition exponentialBackOff() {
        return exponentialBackOff(true);
    }

    public OptimisticLockRetryPolicyDefinition exponentialBackOff(boolean exponentialBackOff) {
        setExponentialBackOff(exponentialBackOff);
        return this;
    }

    public Boolean getExponentialBackOff() {
        return exponentialBackOff;
    }

    public void setExponentialBackOff(Boolean exponentialBackOff) {
        this.exponentialBackOff = exponentialBackOff;
    }

    public OptimisticLockRetryPolicyDefinition randomBackOff() {
        return randomBackOff(true);
    }

    /**
     * Enables random backoff
     */
    public OptimisticLockRetryPolicyDefinition randomBackOff(boolean randomBackOff) {
        setRandomBackOff(randomBackOff);
        return this;
    }

    public Boolean getRandomBackOff() {
        return randomBackOff;
    }

    public void setRandomBackOff(Boolean randomBackOff) {
        this.randomBackOff = randomBackOff;
    }
}