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

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.TimeUnit;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.xml.TimeUnitAdapter;
import org.apache.camel.spi.ThreadPoolProfile;

/**
 * Represents an XML &lt;threadPoolProfile/&gt; element
 *
 * @version 
 */
@XmlRootElement(name = "threadPoolProfile")
@XmlAccessorType(XmlAccessType.FIELD)
public class ThreadPoolProfileDefinition extends OptionalIdentifiedDefinition implements ThreadPoolProfile {
    @XmlAttribute
    private Boolean defaultProfile;
    @XmlAttribute
    private Integer poolSize;
    @XmlAttribute
    private Integer maxPoolSize;
    @XmlAttribute
    private Long keepAliveTime;
    @XmlJavaTypeAdapter(TimeUnitAdapter.class)
    private TimeUnit timeUnit;
    @XmlAttribute
    private Integer maxQueueSize;
    @XmlAttribute
    private ThreadPoolRejectedPolicy rejectedPolicy;

    public ThreadPoolProfileDefinition() {
    }

    public ThreadPoolProfileDefinition(ThreadPoolProfile threadPoolProfile) {
        setDefaultProfile(threadPoolProfile.isDefaultProfile());
        setPoolSize(threadPoolProfile.getPoolSize());
        setMaxPoolSize(threadPoolProfile.getMaxPoolSize());
        setKeepAliveTime(threadPoolProfile.getKeepAliveTime());
        setTimeUnit(threadPoolProfile.getTimeUnit());
        setMaxQueueSize(threadPoolProfile.getMaxQueueSize());
        setRejectedPolicy(threadPoolProfile.getRejectedPolicy());
    }

    public ThreadPoolProfileDefinition poolSize(int poolSize) {
        setPoolSize(poolSize);
        return this;
    }

    public ThreadPoolProfileDefinition maxPoolSize(int maxPoolSize) {
        setMaxPoolSize(maxPoolSize);
        return this;
    }

    public ThreadPoolProfileDefinition keepAliveTime(long keepAliveTime) {
        setKeepAliveTime(keepAliveTime);
        return this;
    }

    public ThreadPoolProfileDefinition timeUnit(TimeUnit timeUnit) {
        setTimeUnit(timeUnit);
        return this;
    }

    public ThreadPoolProfileDefinition maxQueueSize(int maxQueueSize) {
        setMaxQueueSize(maxQueueSize);
        return this;
    }

    public ThreadPoolProfileDefinition rejectedPolicy(ThreadPoolRejectedPolicy rejectedPolicy) {
        setRejectedPolicy(rejectedPolicy);
        return this;
    }

    public Boolean getDefaultProfile() {
        return defaultProfile;
    }

    public void setDefaultProfile(Boolean defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    public Boolean isDefaultProfile() {
        return defaultProfile != null && defaultProfile;
    }

    public Integer getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(Integer poolSize) {
        this.poolSize = poolSize;
    }

    public Integer getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(Integer maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public Long getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setKeepAliveTime(Long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public Integer getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(Integer maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public ThreadPoolRejectedPolicy getRejectedPolicy() {
        return rejectedPolicy;
    }

    public RejectedExecutionHandler getRejectedExecutionHandler() {
        if (rejectedPolicy != null) {
            return rejectedPolicy.asRejectedExecutionHandler();
        }
        return null;
    }

    public void setRejectedPolicy(ThreadPoolRejectedPolicy rejectedPolicy) {
        this.rejectedPolicy = rejectedPolicy;
    }
}
