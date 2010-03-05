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
import java.util.concurrent.TimeUnit;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.builder.xml.TimeUnitAdapter;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;
import org.springframework.beans.factory.FactoryBean;

/**
 * A {@link org.springframework.beans.factory.FactoryBean} which instantiates {@link java.util.concurrent.ExecutorService} objects
 *
 * @version $Revision$
 */
@XmlRootElement(name = "threadPool")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelExecutorServiceFactoryBean extends IdentifiedType implements FactoryBean {

    @XmlAttribute(required = false)
    private Integer poolSize;
    @XmlAttribute(required = false)
    private Integer maxPoolSize;
    @XmlAttribute(required = false)
    private Integer keepAliveTime = 60;
    @XmlAttribute(required = false)
    @XmlJavaTypeAdapter(TimeUnitAdapter.class)
    private TimeUnit units = TimeUnit.SECONDS;
    @XmlAttribute(required = false)
    private String threadName;
    @XmlAttribute
    private Boolean deamon = Boolean.TRUE;

    public Object getObject() throws Exception {
        String name = getThreadName() != null ? getThreadName() : getId();

        ExecutorService answer;
        if (getPoolSize() == null || getPoolSize() <= 0) {
            // use the cached thread pool
            answer = ExecutorServiceHelper.newCachedThreadPool(name, isDeamon());
        } else {
            // use a custom pool based on the settings
            int max = getMaxPoolSize() != null ? getMaxPoolSize() : getPoolSize();
            answer = ExecutorServiceHelper.newThreadPool(name, getPoolSize(), max, getKeepAliveTime(), getUnits(), isDeamon());
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

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public Boolean isDeamon() {
        return deamon;
    }

    public void setDeamon(Boolean deamon) {
        this.deamon = deamon;
    }
}