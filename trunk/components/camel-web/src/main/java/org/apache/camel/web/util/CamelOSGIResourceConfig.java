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
package org.apache.camel.web.util;

import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.MediaType;

import com.sun.jersey.api.core.ClassNamesResourceConfig;
import org.apache.camel.web.resources.CamelContextResource;
import org.apache.camel.web.resources.ComponentResource;
import org.apache.camel.web.resources.ComponentsResource;
import org.apache.camel.web.resources.Constants;
import org.apache.camel.web.resources.ConvertersFromResource;
import org.apache.camel.web.resources.ConvertersResource;
import org.apache.camel.web.resources.EndpointResource;
import org.apache.camel.web.resources.EndpointsResource;
import org.apache.camel.web.resources.ExchangeResource;
import org.apache.camel.web.resources.LanguageResource;
import org.apache.camel.web.resources.LanguagesResource;
import org.apache.camel.web.resources.RouteResource;
import org.apache.camel.web.resources.RouteStatusResource;
import org.apache.camel.web.resources.RoutesResource;

/**
 *
 */
public class CamelOSGIResourceConfig extends ClassNamesResourceConfig {

    public CamelOSGIResourceConfig() {
        super(createProperties());
    }

    protected static Map<String, Object> createProperties() {
        Map<String, Object> properties = new HashMap<String, Object>();

        String[] resources = new String[] {
               CamelContextResource.class.getName(),
               ComponentResource.class.getName(),
               ComponentsResource.class.getName(),
               ConvertersFromResource.class.getName(),
               ConvertersResource.class.getName(),
               EndpointResource.class.getName(),
               EndpointsResource.class.getName(),
               ExchangeResource.class.getName(),
               LanguageResource.class.getName(),
               LanguagesResource.class.getName(),
               RouteResource.class.getName(),
               RoutesResource.class.getName(),
               RouteStatusResource.class.getName(),
               JAXBContextResolverOSGI.class.getName(),
               JAXBMarshallerResolver.class.getName()
        };

        properties.put(ClassNamesResourceConfig.PROPERTY_CLASSNAMES, resources);
        
        return properties;

    }

    public Map<String, MediaType> getMediaTypeMappings() {
        Map<String, MediaType> m = new HashMap<String, MediaType>();
        m.put("html", MediaType.TEXT_HTML_TYPE);
        m.put("xml", MediaType.APPLICATION_XML_TYPE);
        m.put("json", MediaType.APPLICATION_JSON_TYPE);
        m.put("dot", MediaType.valueOf(Constants.DOT_MIMETYPE));
        return m;
    }
}