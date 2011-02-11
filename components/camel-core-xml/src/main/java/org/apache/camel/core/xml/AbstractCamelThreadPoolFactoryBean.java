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

/**
 * A factory which instantiates {@link java.util.concurrent.ExecutorService} objects
 *
 * @version $Revision: 925208 $
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class AbstractCamelThreadPoolFactoryBean extends AbstractCamelFactoryBean<ExecutorService> {

    @XmlAttribute(required = true)
    private Integer poolSize;
    @XmlAttribute
    private Integer maxPoolSize;
    @XmlAttribute
    private Long keepAliveTime = 60L;
    @XmlAttribute
    @XmlJavaTypeAdapter(TimeUnitAdapter.class)
    private TimeUnit timeUnit = TimeUnit.SECONDS;
    @XmlAttribute
    private Integer maxQueueSize = -1;
    @XmlAttribute
    private ThreadPoolRejectedPolicy rejectedPolicy = ThreadPoolRejectedPolicy.CallerRuns;
    @XmlAttribute(required = true)
    private String threadName;
    @XmlAttribute
    private Boolean daemon = Boolean.TRUE;

    public ExecutorService getObject() throws Exception {
        if (poolSize == null || poolSize <= 0) {
            throw new IllegalArgumentException("PoolSize must be a positive number");
        }

        int max = getMaxPoolSize() != null ? getMaxPoolSize() : getPoolSize();
        RejectedExecutionHandler rejected = null;
        if (rejectedPolicy != null) {
            rejected = rejectedPolicy.asRejectedExecutionHandler();
        }

        ExecutorService answer = getCamelContext().getExecutorServiceStrategy().newThreadPool(getId(), getThreadName(), getPoolSize(), max,
                    getKeepAliveTime(), getTimeUnit(), getMaxQueueSize(), rejected, isDaemon());
        return answer;
    }

    protected abstract CamelContext getCamelContextWithId(String camelContextId);

    public Class<ExecutorService> getObjectType() {
        return ExecutorService.class;
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

    public void setRejectedPolicy(ThreadPoolRejectedPolicy rejectedPolicy) {
        this.rejectedPolicy = rejectedPolicy;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public Boolean isDaemon() {
        return daemon;
    }

    public void setDaemon(Boolean daemon) {
        this.daemon = daemon;
    }

}