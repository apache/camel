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
package org.apache.camel.core.xml;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;

/**
 * A factory which instantiates {@link java.util.concurrent.ExecutorService} objects
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractCamelThreadPoolFactoryBean extends AbstractCamelFactoryBean<ExecutorService> {

    @XmlAttribute(required = true)
    @Metadata(description = "Sets the core pool size (threads to keep minimum in pool)")
    private String poolSize;
    @XmlAttribute
    @Metadata(description = "Sets the maximum pool size")
    private String maxPoolSize;
    @XmlAttribute
    @Metadata(description = "Sets the keep alive time for inactive threads")
    private String keepAliveTime;
    @XmlAttribute
    @Metadata(description = "Sets the time unit used for keep alive time", defaultValue = "SECONDS", javaType = "java.util.concurrent.TimeUnit",
            enums = "NANOSECONDS,MICROSECONDS,MILLISECONDS,SECONDS,MINUTES,HOURS,DAYS")
    private String timeUnit = TimeUnit.SECONDS.name();
    @XmlAttribute
    @Metadata(description = "Sets the maximum number of tasks in the work queue. Use -1 for an unbounded queue")
    private String maxQueueSize;
    @XmlAttribute
    @Metadata(description = "Sets whether to allow core threads to timeout")
    private String allowCoreThreadTimeOut;
    @XmlAttribute
    @Metadata(description = "Sets the handler for tasks which cannot be executed by the thread pool.", defaultValue = "CallerRuns")
    private ThreadPoolRejectedPolicy rejectedPolicy = ThreadPoolRejectedPolicy.CallerRuns;
    @XmlAttribute(required = true)
    @Metadata(description = "To use a custom thread name / pattern")
    private String threadName;
    @XmlAttribute
    @Metadata(description = "Whether to use a scheduled thread pool", defaultValue = "false")
    private Boolean scheduled;

    @Override
    public ExecutorService getObject() throws Exception {
        int size = CamelContextHelper.parseInteger(getCamelContext(), poolSize);
        if (size <= 0) {
            throw new IllegalArgumentException("PoolSize must be a positive number");
        }

        int max = size;
        if (maxPoolSize != null) {
            max = CamelContextHelper.parseInteger(getCamelContext(), maxPoolSize);
        }

        long keepAlive = 60;
        if (keepAliveTime != null) {
            keepAlive = CamelContextHelper.parseLong(getCamelContext(), keepAliveTime);
        }

        int queueSize = -1;
        if (maxQueueSize != null) {
            queueSize = CamelContextHelper.parseInteger(getCamelContext(), maxQueueSize);
        }

        boolean allow = false;
        if (allowCoreThreadTimeOut != null) {
            allow = CamelContextHelper.parseBoolean(getCamelContext(), allowCoreThreadTimeOut);
        }
        TimeUnit tu = CamelContextHelper.parse(getCamelContext(), TimeUnit.class, timeUnit);

        ThreadPoolProfile profile = new ThreadPoolProfileBuilder(getId())
                .poolSize(size)
                .maxPoolSize(max)
                .keepAliveTime(keepAlive, tu)
                .maxQueueSize(queueSize)
                .allowCoreThreadTimeOut(allow)
                .rejectedPolicy(rejectedPolicy)
                .build();

        ExecutorService answer;
        if (scheduled != null && scheduled) {
            answer = getCamelContext().getExecutorServiceManager().newScheduledThreadPool(getId(), getThreadName(), profile);
        } else {
            answer = getCamelContext().getExecutorServiceManager().newThreadPool(getId(), getThreadName(), profile);
        }
        return answer;
    }

    @Override
    public Class<ExecutorService> getObjectType() {
        return ExecutorService.class;
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

    public String getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(String timeUnit) {
        this.timeUnit = timeUnit;
    }

    public String getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(String maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public String getAllowCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    public void setAllowCoreThreadTimeOut(String allowCoreThreadTimeOut) {
        this.allowCoreThreadTimeOut = allowCoreThreadTimeOut;
    }

    public ThreadPoolRejectedPolicy getRejectedPolicy() {
        return rejectedPolicy;
    }

    public void setRejectedPolicy(ThreadPoolRejectedPolicy rejectedPolicy) {
        this.rejectedPolicy = rejectedPolicy;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public Boolean getScheduled() {
        return scheduled;
    }

    public void setScheduled(Boolean scheduled) {
        this.scheduled = scheduled;
    }

}
