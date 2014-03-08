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

import java.util.Map;
import java.util.TreeMap;
import javax.annotation.PreDestroy;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.sun.jersey.api.core.InjectParam;
import com.sun.jersey.api.view.ImplicitProduces;
import com.sun.jersey.spi.resource.Singleton;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.web.model.Camel;

/**
 * The root Camel resource from which all other resources can be navigated such as for <code>endpoints</code>
 * or <code>routes</code>
 */
@Path("/")
@ImplicitProduces(Constants.HTML_MIME_TYPES)
@Singleton
public class CamelContextResource {
    
    private CamelContext camelContext;
    private ProducerTemplate template;

    public CamelContextResource(@InjectParam CamelContext camelContext) throws Exception {
        this.camelContext = camelContext;
        this.template = camelContext.createProducerTemplate();
        template.start();
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public ProducerTemplate getTemplate() {
        return template;
    }

    public String getName() {
        return camelContext.getName();
    }

    public String getVersion() {
        if (camelContext instanceof ServiceSupport) {
            ServiceSupport serviceSupport = (ServiceSupport) camelContext;
            return serviceSupport.getVersion();
        }
        return null;
    }

    @PreDestroy
    public void close() throws Exception {
        if (template != null) {
            template.stop();
        }
    }

    /**
     * Returns the system properties
     */
    public Map<Object, Object> getSystemProperties() {
        return new TreeMap<Object, Object>(System.getProperties());
    }

    // representations
    //-------------------------------------------------------------------------

    /**
     * Returns the XML or JSON representation of the CamelContext
     */
    @GET
    // TODO we can replace this long expression with a static constant
    // when Jersey supports JAX-RS 1.1
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Camel getCamel() {
        return new Camel(camelContext, getVersion());
    }

    /**
     * Returns the active components
     */
    @Path("components")
    public ComponentsResource getComponents() {
        return new ComponentsResource(this);
    }

    /**
     * Returns the active type converters
     */
    @Path("converters")
    public ConvertersResource getConvertersResource() {
        return new ConvertersResource(this);
    }

    /**
     * Returns the active endpoints
     */
    @Path("endpoints")
    public EndpointsResource getEndpointsResource() {
        return new EndpointsResource(this);
    }

    /**
     * Returns the active languages
     */
    @Path("languages")
    public LanguagesResource getLanguages() {
        return new LanguagesResource(this);
    }

    /**
     * Returns the active routes
     */
    @Path("routes")
    public RoutesResource getRoutesResource() {
        return new RoutesResource(this);
    }


}
