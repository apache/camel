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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class ThreadPools {

    @XmlElement(name = "threadPool")
    private List<ThreadPool> threadPools = new ArrayList<ThreadPool>();

    @Override
    public String toString() {
        return "Thread pools " + threadPools;
    }

    public List<ThreadPool> getThreadPools() {
		return threadPools;
	}

	public void setThreadPools(List<ThreadPool> threadPools) {
		this.threadPools = threadPools;
	}

	public void load(List<CamelDataBean> managedBeans) {
        for(CamelDataBean managedBean : managedBeans) {
        	addThreadPool(createThreadPool(managedBean));
        }
    }

    protected ThreadPool createThreadPool(CamelDataBean bean) {
    	ThreadPool threadPool = new ThreadPool();
    	threadPool.load(bean);
        return threadPool;
    }

    public void addThreadPool(ThreadPool threadPool) {
        getThreadPools().add(threadPool);
    }

}