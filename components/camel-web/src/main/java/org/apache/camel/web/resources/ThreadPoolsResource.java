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
package org.apache.camel.web.resources;

import com.sun.jersey.api.view.ImplicitProduces;

import org.apache.camel.web.connectors.CamelConnection;
import org.apache.camel.web.connectors.CamelDataBean;
import org.apache.camel.web.model.ThreadPool;
import org.apache.camel.web.model.ThreadPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 *
 */
@ImplicitProduces(Constants.HTML_MIME_TYPES)
public class ThreadPoolsResource {

    private static final transient Logger LOG = LoggerFactory.getLogger(ThreadPoolsResource.class);

    private CamelConnection camelConnection;

    public ThreadPoolsResource(CamelConnection camelConnection) {
        this.camelConnection = camelConnection;
    }
    
    /**
     * Returns the resource of an individual Camel thread pool
     *
     * @param name the Thread pool unique name
     */
    @Path("{name}")
    public ThreadPoolResource getThreadPool(@PathParam("name") String name) {
    	LOG.info("Retrieving thread pool " + name);
        CamelDataBean threadPoolBean = camelConnection.getCamelBean("threadpools", "\"" + name + "\"");
    	
        if (threadPoolBean != null) {
        	ThreadPool threadPool = new ThreadPool();
        	threadPool.load(threadPoolBean);
            return new ThreadPoolResource(threadPool, camelConnection);
        } else {
            LOG.warn("No thread pool found for name: " + name);
            return null;
        }
    }

    /**
     * Returns a list of consumers available in this context
     */
    @GET
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public ThreadPools getDTO() {
        if(LOG.isDebugEnabled()) {
            LOG.debug("Retrieving thread pools.");
        }

        List<CamelDataBean> threadPoolsCamelBeans = camelConnection.getCamelBeans("threadpools");
        ThreadPools threadPools = new ThreadPools();
        threadPools.load(threadPoolsCamelBeans);
        return threadPools;
    }

    public List<ThreadPool> getThreadPools() {
        return getDTO().getThreadPools();
    }

}
