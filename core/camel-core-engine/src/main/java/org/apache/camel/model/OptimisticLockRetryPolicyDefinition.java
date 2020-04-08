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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * To configure optimistic locking
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "optimisticLockRetryPolicy")
@XmlAccessorType(XmlAccessType.FIELD)
public class OptimisticLockRetryPolicyDefinition {
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer")
    private String maximumRetries;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Long", defaultValue = "50")
    private String retryDelay;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Long", defaultValue = "1000")
    private String maximumRetryDelay;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true")
    private String exponentialBackOff;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String randomBackOff;

    public OptimisticLockRetryPolicyDefinition() {
    }

    /**
     * Sets the maximum number of retries
     */
    public OptimisticLockRetryPolicyDefinition maximumRetries(int maximumRetries) {
        return maximumRetries(String.valueOf(maximumRetries));
    }

    public OptimisticLockRetryPolicyDefinition maximumRetries(String maximumRetries) {
        setMaximumRetries(maximumRetries);
        return this;
    }

    public String getMaximumRetries() {
        return maximumRetries;
    }

    public void setMaximumRetries(String maximumRetries) {
        this.maximumRetries = maximumRetries;
    }

    /**
     * Sets the delay in millis between retries
     */
    public OptimisticLockRetryPolicyDefinition retryDelay(long retryDelay) {
        return retryDelay(Long.toString(retryDelay));
    }

    /**
     * Sets the delay in millis between retries
     */
    public OptimisticLockRetryPolicyDefinition retryDelay(String retryDelay) {
        setRetryDelay(retryDelay);
        return this;
    }

    public String getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(String retryDelay) {
        this.retryDelay = retryDelay;
    }

    /**
     * Sets the upper value of retry in millis between retries, when using
     * exponential or random backoff
     */
    public OptimisticLockRetryPolicyDefinition maximumRetryDelay(long maximumRetryDelay) {
        return maximumRetryDelay(Long.toString(maximumRetryDelay));
    }

    /**
     * Sets the upper value of retry in millis between retries, when using
     * exponential or random backoff
     */
    public OptimisticLockRetryPolicyDefinition maximumRetryDelay(String maximumRetryDelay) {
        setMaximumRetryDelay(maximumRetryDelay);
        return this;
    }

    public String getMaximumRetryDelay() {
        return maximumRetryDelay;
    }

    public void setMaximumRetryDelay(String maximumRetryDelay) {
        this.maximumRetryDelay = maximumRetryDelay;
    }

    /**
     * Enable exponential backoff
     */
    public OptimisticLockRetryPolicyDefinition exponentialBackOff() {
        return exponentialBackOff(true);
    }

    public OptimisticLockRetryPolicyDefinition exponentialBackOff(boolean exponentialBackOff) {
        return exponentialBackOff(Boolean.toString(exponentialBackOff));
    }

    public OptimisticLockRetryPolicyDefinition exponentialBackOff(String exponentialBackOff) {
        setExponentialBackOff(exponentialBackOff);
        return this;
    }

    public String getExponentialBackOff() {
        return exponentialBackOff;
    }

    public void setExponentialBackOff(String exponentialBackOff) {
        this.exponentialBackOff = exponentialBackOff;
    }

    public OptimisticLockRetryPolicyDefinition randomBackOff() {
        return randomBackOff(true);
    }

    /**
     * Enables random backoff
     */
    public OptimisticLockRetryPolicyDefinition randomBackOff(boolean randomBackOff) {
        return randomBackOff(String.valueOf(randomBackOff));
    }

    public OptimisticLockRetryPolicyDefinition randomBackOff(String randomBackOff) {
        setRandomBackOff(randomBackOff);
        return this;
    }

    public String getRandomBackOff() {
        return randomBackOff;
    }

    public void setRandomBackOff(String randomBackOff) {
        this.randomBackOff = randomBackOff;
    }
}
