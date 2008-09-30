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
package org.apache.camel.rest.util;



import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;

import com.sun.jersey.api.json.JSONJAXBContext;

import org.apache.camel.model.RouteType;
import org.apache.camel.model.RoutesType;
import org.apache.camel.rest.model.EndpointLink;
import org.apache.camel.rest.model.Endpoints;

/**
 * @version $Revision$
 */
@Provider
public final class JAXBContextResolver implements ContextResolver<JAXBContext> {

    private final JAXBContext context;

    public JAXBContextResolver() throws Exception {
        Map<String, Object> props = new HashMap<String, Object>();
        //props.put(JSONJAXBContext.JSON_NOTATION, "MAPPED");
        props.put(JSONJAXBContext.JSON_ROOT_UNWRAPPING, Boolean.TRUE);
        props.put(JSONJAXBContext.JSON_NON_STRINGS, "[\"number\"]");

        this.context = new JSONJAXBContext(getJaxbClasses(), props);
    }

    protected Class[] getJaxbClasses() {
        return new Class[]{RoutesType.class, RouteType.class,
                           Endpoints.class, EndpointLink.class};
    }

    public JAXBContext getContext(Class<?> objectType) {
        String name = objectType.getPackage().getName();
        if (name.startsWith("org.apache.camel.model") || name.startsWith("org.apache.camel.rest.model")) {
            return context;
        }
        return null;
    }
}