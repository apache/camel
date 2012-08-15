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