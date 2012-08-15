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
package org.apache.camel.web.model;

import org.apache.camel.web.connectors.CamelDataBean;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Thread Pool
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class ThreadPool {
	
	public static final String PROPERTY_ACTIVE_COUNT = "ActiveCount";
	public static final String PROPERTY_CORE_POOL_SIZE = "CorePoolSize";
	public static final String PROPERTY_POOL_SIZE = "PoolSize";
	public static final String PROPERTY_MAXIMUM_POOL_SIZE = "MaximumPoolSize";
	public static final String PROPERTY_TASK_COUNT = "TaskCount";
	public static final String PROPERTY_KEEP_ALIVE_TIME = "KeepAliveTime";

    @XmlAttribute
    private String name;

    private String description;

    private Integer activeCount;

    private Integer corePoolSize;

    private Integer poolSize;
    
    private Integer maximumPoolSize;
    
    private Long taskCount;
    
    private Long keepAliveTime;

    public void load(CamelDataBean bean) {
        name = bean.getName();
        description = bean.getDescription();
        activeCount = (Integer) bean.getProperty(PROPERTY_ACTIVE_COUNT);
        corePoolSize = (Integer) bean.getProperty(PROPERTY_CORE_POOL_SIZE);
        poolSize = (Integer) bean.getProperty(PROPERTY_POOL_SIZE);
        maximumPoolSize = (Integer) bean.getProperty(PROPERTY_MAXIMUM_POOL_SIZE);
        taskCount = (Long) bean.getProperty(PROPERTY_TASK_COUNT);
        keepAliveTime = (Long) bean.getProperty(PROPERTY_KEEP_ALIVE_TIME);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

	public Integer getActiveCount() {
		return activeCount;
	}

	public void setActiveCount(Integer activeCount) {
		this.activeCount = activeCount;
	}

	public Integer getCorePoolSize() {
		return corePoolSize;
	}

	public void setCorePoolSize(Integer corePoolSize) {
		this.corePoolSize = corePoolSize;
	}

	public Integer getPoolSize() {
		return poolSize;
	}

	public void setPoolSize(Integer poolSize) {
		this.poolSize = poolSize;
	}

	public Integer getMaximumPoolSize() {
		return maximumPoolSize;
	}

	public void setMaximumPoolSize(Integer maximumPoolSize) {
		this.maximumPoolSize = maximumPoolSize;
	}

	public Long getTaskCount() {
		return taskCount;
	}

	public void setTaskCount(Long taskCount) {
		this.taskCount = taskCount;
	}

	public Long getKeepAliveTime() {
		return keepAliveTime;
	}

	public void setKeepAliveTime(Long keepAliveTime) {
		this.keepAliveTime = keepAliveTime;
	}
    
}
