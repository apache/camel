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
package org.apache.camel.core.xml;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.TimeUnit;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.CamelContext;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.xml.TimeUnitAdapter;
import org.apache.camel.util.CamelContextHelper;

/**
 * A factory which instantiates {@link java.util.concurrent.ExecutorService} objects
 *
 * @version 
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractCamelThreadPoolFactoryBean extends AbstractCamelFactoryBean<ExecutorService> {

    @XmlAttribute(required = true)
    private String poolSize;
    @XmlAttribute
    private String maxPoolSize;
    @XmlAttribute
    private String keepAliveTime;
    @XmlAttribute
    @XmlJavaTypeAdapter(TimeUnitAdapter.class)
    private TimeUnit timeUnit = TimeUnit.SECONDS;
    @XmlAttribute
    private String maxQueueSize;
    @XmlAttribute
    private ThreadPoolRejectedPolicy rejectedPolicy = ThreadPoolRejectedPolicy.CallerRuns;
    @XmlAttribute(required = true)
    private String threadName;

    public ExecutorService getObject() throws Exception {
        int size = CamelContextHelper.parseInteger(getCamelContext(), poolSize);
        if (size <= 0) {
            throw new IllegalArgumentException("PoolSize must be a positive number");
        }

        int max = size;
        if (maxPoolSize != null) {
            max = CamelContextHelper.parseInteger(getCamelContext(), maxPoolSize);
        }

        RejectedExecutionHandler rejected = null;
        if (rejectedPolicy != null) {
            rejected = rejectedPolicy.asRejectedExecutionHandler();
        }

        long keepAlive = 60;
        if (keepAliveTime != null) {
            keepAlive = CamelContextHelper.parseLong(getCamelContext(), keepAliveTime);
        }

        int queueSize = -1;
        if (maxQueueSize != null) {
            queueSize = CamelContextHelper.parseInteger(getCamelContext(), maxQueueSize);
        }

        ExecutorService answer = getCamelContext().getExecutorServiceStrategy().newThreadPool(getId(), getThreadName(),
                size, max, keepAlive, getTimeUnit(), queueSize, rejected, true);
        return answer;
    }

    protected abstract CamelContext getCamelContextWithId(String camelContextId);

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

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public String getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(String maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
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


}