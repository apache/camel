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

import com.sun.jersey.api.representation.Form;
import org.apache.camel.web.connectors.CamelConnection;
import org.apache.camel.web.model.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;

/**
 * A single Camel Consumer.
 */
public class ConsumerResource {

    private static final transient Logger LOG = LoggerFactory.getLogger(ConsumerResource.class);

    private Consumer consumer;

    private CamelConnection connection;

    public ConsumerResource(Consumer consumer, CamelConnection connection) {
        this.consumer = consumer;
        this.connection = connection;
    }

    /**
     * Returns the XML or JSON representation of this consumer
     */
    @GET
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Consumer getConsumer() {
        return consumer;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getStatusText() {
        return consumer.getStatus();
    }

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    public Response setStatus(String status) throws Exception {
        if (status != null) {
            if (status.equalsIgnoreCase("start")) {
                connection.invokeOperation("consumers", consumer.getName(), "start", new Object[0], new String[0]);
                return Response.ok().build();
            } else if (status.equalsIgnoreCase("stop")) {
                connection.invokeOperation("consumers", consumer.getName(), "stop", new Object[0], new String[0]);
                return Response.ok().build();
            }
        }
        return Response.noContent().build();
    }


    /**
     * Sets the status of this route to either "start" or "stop"
     *
     * @param formData is the form data POSTed typically from a HTML form with the <code>status</code> field
     *                 set to either "start" or "stop"
     */
    @POST
    @Consumes("application/x-www-form-urlencoded")
    public Response setStatus(Form formData) throws Exception {
        // TODO replace the Form class with an injected bean?
        String status = formData.getFirst("status", String.class);
        setStatus(status);
        return Response.seeOther(new URI("/consumers")).build();
    }

}
