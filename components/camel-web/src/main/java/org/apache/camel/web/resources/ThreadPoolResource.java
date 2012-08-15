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

import java.net.URI;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.camel.web.connectors.CamelConnection;
import org.apache.camel.web.model.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.representation.Form;

/**
 * A single Thread Pool
 */
public class ThreadPoolResource {

    private static final transient Logger LOG = LoggerFactory.getLogger(ThreadPoolResource.class);

    private ThreadPool threadPool;

    private CamelConnection connection;

    public ThreadPoolResource(ThreadPool threadPool, CamelConnection connection) {
        this.threadPool = threadPool;
        this.connection = connection;
    }
    
    public ThreadPool getThreadPool() {
        return threadPool;
    }
    
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Path("/corePoolSize")
    public Response setCorePoolSize(Integer corePoolSize) throws Exception {
        connection.invokeOperation("threadpools", "\"" + threadPool.getName() + "\"", "setCorePoolSize", new Integer[] {corePoolSize}, new String[] {"int"});
        return Response.ok().build();
    }


    /**
     * Sets the core pool size of this thread pool
     *
     * @param formData is the form data POSTed typically from a HTML form with the <code>corePoolSize</code> field
     */
    @POST
    @Consumes("application/x-www-form-urlencoded")
    @Path("/corePoolSize")
    public Response setCorePoolSize(Form formData) throws Exception {
        Integer corePoolSize = formData.getFirst("corePoolSize", Integer.class);
        setCorePoolSize(corePoolSize);
        return Response.seeOther(new URI("/threadpools/" + threadPool.getName())).build();
    }
    
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Path("/maximumPoolSize")
    public Response setMaximumPoolSize(Integer maximumPoolSize) throws Exception {
        connection.invokeOperation("threadpools", "\"" + threadPool.getName() + "\"", "setMaximumPoolSize", new Integer[] {maximumPoolSize}, new String[] {"int"});
        return Response.ok().build();
    }


    /**
     * Sets the maximum pool size of this thread pool
     *
     * @param formData is the form data POSTed typically from a HTML form with the <code>maximumPoolSize</code> field
     */
    @POST
    @Consumes("application/x-www-form-urlencoded")
    @Path("/maximumPoolSize")
    public Response setMaximumPoolSize(Form formData) throws Exception {
        Integer maximumPoolSize = formData.getFirst("maximumPoolSize", Integer.class);
        setMaximumPoolSize(maximumPoolSize);
        return Response.seeOther(new URI("/threadpools/" + threadPool.getName())).build();
    }
    
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Path("/keepAliveTime")
    public Response setKeepAliveTime(Integer keepAliveTime) throws Exception {
        connection.invokeOperation("threadpools", "\"" + threadPool.getName() + "\"", "setKeepAliveTime", new Integer[] {keepAliveTime}, new String[] {"long"});
        return Response.ok().build();
    }


    /**
     * Sets the keep alive time of this thread pool
     *
     * @param formData is the form data POSTed typically from a HTML form with the <code>keepAliveTime</code> field
     */
    @POST
    @Consumes("application/x-www-form-urlencoded")
    @Path("/keepAliveTime")
    public Response setKeepAliveTime(Form formData) throws Exception {
        Integer keepAliveTime = formData.getFirst("keepAliveTime", Integer.class);
        setKeepAliveTime(keepAliveTime);
        return Response.seeOther(new URI("/threadpools/" + threadPool.getName())).build();
    }
    
    /**
     * Purge this thread pool
     *
     */
    @GET
    @Path("/purge")
    public Response purge() throws Exception {
    	connection.invokeOperation("threadpools", "\"" + threadPool.getName() + "\"", "purge", new Object[0], new String[0]);
        return Response.seeOther(new URI("/threadpools/" + threadPool.getName())).build();
    }
    
    /**
     * Gets pool size of this thread
     */
    @GET
    @Path("/poolSize")
    @Produces(MediaType.TEXT_PLAIN)
    public String getThreadPoolSize() throws Exception {
    	Integer poolSize = (Integer) connection.invokeOperation("threadpools", "\"" + threadPool.getName() + "\"", "getPoolSize", new Object[0], new String[0]);
    	return poolSize.toString();
    }

}
