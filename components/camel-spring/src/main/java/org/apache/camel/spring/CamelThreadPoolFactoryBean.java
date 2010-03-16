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
package org.apache.camel.spring;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.TimeUnit;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.xml.TimeUnitAdapter;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.spring.util.CamelContextResolverHelper;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * A {@link org.springframework.beans.factory.FactoryBean} which instantiates {@link java.util.concurrent.ExecutorService} objects
 *
 * @version $Revision$
 */
@XmlRootElement(name = "threadPool")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelThreadPoolFactoryBean extends IdentifiedType implements FactoryBean, CamelContextAware, ApplicationContextAware {

    @XmlAttribute
    private Integer poolSize;
    @XmlAttribute
    private Integer maxPoolSize;
    @XmlAttribute
    private Integer keepAliveTime = 60;
    @XmlAttribute
    @XmlJavaTypeAdapter(TimeUnitAdapter.class)
    private TimeUnit units = TimeUnit.SECONDS;
    @XmlAttribute
    private Integer maxQueueSize = -1;
    @XmlAttribute
    private ThreadPoolRejectedPolicy rejectedPolicy;
    @XmlAttribute
    private String threadName;
    @XmlAttribute
    private Boolean daemon = Boolean.TRUE;
    @XmlAttribute
    private String camelContextId;
    @XmlTransient
    private CamelContext camelContext;
    @XmlTransient
    private ApplicationContext applicationContext;

    public Object getObject() throws Exception {
        if (camelContext == null && camelContextId != null) {
            camelContext = CamelContextResolverHelper.getCamelContextWithId(applicationContext, camelContextId);
        }
        notNull(camelContext, "camelContext");

        String name = getThreadName() != null ? getThreadName() : getId();

        ExecutorService answer;
        if (getPoolSize() == null || getPoolSize() <= 0) {
            // use the default profile
            answer = camelContext.getExecutorServiceStrategy().newDefaultThreadPool(getId(), name);
        } else {
            // use a custom pool based on the settings
            int max = getMaxPoolSize() != null ? getMaxPoolSize() : getPoolSize();
            RejectedExecutionHandler rejected = null;
            if (rejectedPolicy != null) {
                rejected = rejectedPolicy.asRejectedExecutionHandler();
            }
            answer = camelContext.getExecutorServiceStrategy().newThreadPool(getId(), name, getPoolSize(), max,
                    getKeepAliveTime(), getUnits(), getMaxQueueSize(), rejected, isDaemon());
        }
        return answer;
    }

    public Class getObjectType() {
        return ExecutorService.class;
    }

    public boolean isSingleton() {
        return true;
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

    public Integer getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setKeepAliveTime(Integer keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public TimeUnit getUnits() {
        return units;
    }

    public void setUnits(TimeUnit units) {
        this.units = units;
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

    public String getCamelContextId() {
        return camelContextId;
    }

    public void setCamelContextId(String camelContextId) {
        this.camelContextId = camelContextId;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
}