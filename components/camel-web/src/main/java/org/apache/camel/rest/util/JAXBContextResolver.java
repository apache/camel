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


import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.api.json.JSONJAXBContext;
import org.apache.camel.model.RouteType;
import org.apache.camel.model.RoutesType;
import org.apache.camel.model.Constants;
import org.apache.camel.rest.model.Camel;
import org.apache.camel.rest.model.EndpointLink;
import org.apache.camel.rest.model.Endpoints;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @version $Revision$
 */
@Provider
public final class JAXBContextResolver implements ContextResolver<JAXBContext> {

    private final JAXBContext context;
    private String packages;
    public JAXBContextResolver() throws Exception {

        // TODO we can't use natural with JAXB 2.1.6 or 2.1 for some reason?
        JSONConfiguration.Builder builder = JSONConfiguration.mapped();
        //JSONConfiguration.Builder builder = JSONConfiguration.natural();
        //this.context = new JSONJAXBContext(builder.build(), cTypes);
        this.packages = Constants.JAXB_CONTEXT_PACKAGES + ":org.apache.camel.rest.model";
        this.context = new JSONJAXBContext(builder.build(), packages);
    }

    public JAXBContext getContext(Class<?> objectType) {
        Package aPackage = objectType.getPackage();
        if (aPackage != null) {
            String name = aPackage.getName();
            if (name.length() > 0) {
                if (packages.contains(name)) {
                    return context;
                }
            }
        }
        return null;
    }
}
