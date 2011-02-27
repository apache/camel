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

import org.apache.camel.CamelContext;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.xml.TimeUnitAdapter;
import org.apache.camel.impl.ThreadPoolProfileSupport;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;threadPoolProfile/&gt; element
 *
 * @version 
 */
@XmlRootElement(name = "threadPoolProfile")
@XmlAccessorType(XmlAccessType.FIELD)
public class ThreadPoolProfileDefinition extends OptionalIdentifiedDefinition {
    @XmlAttribute
    private Boolean defaultProfile;
    @XmlAttribute
    private String poolSize;
    @XmlAttribute
    private String maxPoolSize;
    @XmlAttribute
    private String keepAliveTime;
    @XmlJavaTypeAdapter(TimeUnitAdapter.class)
    private TimeUnit timeUnit;
    @XmlAttribute
    private String maxQueueSize;
    @XmlAttribute
    private ThreadPoolRejectedPolicy rejectedPolicy;

    public ThreadPoolProfileDefinition() {
    }

    public ThreadPoolProfileDefinition(ThreadPoolProfile threadPoolProfile) {
        setDefaultProfile(threadPoolProfile.isDefaultProfile());
        setPoolSize("" + threadPoolProfile.getPoolSize());
        setMaxPoolSize("" + threadPoolProfile.getMaxPoolSize());
        setKeepAliveTime("" + threadPoolProfile.getKeepAliveTime());
        setTimeUnit(threadPoolProfile.getTimeUnit());
        setMaxQueueSize("" + threadPoolProfile.getMaxQueueSize());
        setRejectedPolicy(threadPoolProfile.getRejectedPolicy());
    }

    public ThreadPoolProfileDefinition poolSize(int poolSize) {
        return poolSize("" + poolSize);
    }

    public ThreadPoolProfileDefinition poolSize(String poolSize) {
        setPoolSize(poolSize);
        return this;
    }

    public ThreadPoolProfileDefinition maxPoolSize(int maxPoolSize) {
        return maxPoolSize("" + maxQueueSize);
    }

    public ThreadPoolProfileDefinition maxPoolSize(String maxPoolSize) {
        setMaxPoolSize("" + maxPoolSize);
        return this;
    }

    public ThreadPoolProfileDefinition keepAliveTime(long keepAliveTime) {
        return keepAliveTime("" + keepAliveTime);
    }

    public ThreadPoolProfileDefinition keepAliveTime(String keepAliveTime) {
        setKeepAliveTime("" + keepAliveTime);
        return this;
    }

    public ThreadPoolProfileDefinition timeUnit(TimeUnit timeUnit) {
        setTimeUnit(timeUnit);
        return this;
    }

    public ThreadPoolProfileDefinition maxQueueSize(int maxQueueSize) {
        return maxQueueSize("" + maxQueueSize);
    }

    public ThreadPoolProfileDefinition maxQueueSize(String maxQueueSize) {
        setMaxQueueSize("" + maxQueueSize);
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

    public String getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(String poolSize) {
        this.poolSize = poolSize;
    }

    public String getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(String maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public String getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setKeepAliveTime(String keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public String getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(String maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
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

    /**
     * Creates a {@link ThreadPoolProfile} instance based on this definition.
     *
     * @param context    the camel context
     * @return           the profile
     * @throws Exception is thrown if error creating the profile
     */
    public ThreadPoolProfile asThreadPoolProfile(CamelContext context) throws Exception {
        ObjectHelper.notNull(context, "CamelContext", this);

        ThreadPoolProfileSupport answer = new ThreadPoolProfileSupport(getId());
        answer.setDefaultProfile(getDefaultProfile());
        answer.setPoolSize(CamelContextHelper.parseInteger(context, getPoolSize()));
        answer.setMaxPoolSize(CamelContextHelper.parseInteger(context, getMaxPoolSize()));
        answer.setKeepAliveTime(CamelContextHelper.parseLong(context, getKeepAliveTime()));
        answer.setMaxQueueSize(CamelContextHelper.parseInteger(context, getMaxQueueSize()));
        answer.setRejectedPolicy(getRejectedPolicy());
        answer.setTimeUnit(getTimeUnit());
        return answer;
    }
}
