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
package org.apache.camel.api.management.mbean;

import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularType;

/**
 * Various JMX openmbean types used by Camel.
 */
public final class CamelOpenMBeanTypes {

    private CamelOpenMBeanTypes() {
    }

    public static TabularType listTypeConvertersTabularType() throws OpenDataException {
        CompositeType ct = listTypeConvertersCompositeType();
        return new TabularType("listTypeConverters", "Lists all the type converters in the registry (from -> to)", ct, new String[]{"from", "to"});
    }

    public static CompositeType listTypeConvertersCompositeType() throws OpenDataException {
        return new CompositeType("types", "From/To types", new String[]{"from", "to"},
                new String[]{"From type", "To type"},
                new OpenType[]{SimpleType.STRING, SimpleType.STRING});
    }

    public static TabularType listRestServicesTabularType() throws OpenDataException {
        CompositeType ct = listRestServicesCompositeType();
        return new TabularType("listRestServices", "Lists all the rest services in the registry", ct, new String[]{"url", "method"});
    }

    public static CompositeType listRestServicesCompositeType() throws OpenDataException {
        return new CompositeType("rests", "Rest Services", new String[]{"url", "baseUrl", "basePath", "uriTemplate", "method", "consumes", "produces", "inType", "outType", "state"},
                new String[]{"Url", "Base Url", "Base Path", "Uri Template", "Method", "Consumes", "Produces", "Input Type", "Output Type", "State"},
                new OpenType[]{SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING,
                               SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING});
    }

    public static TabularType listEndpointsTabularType() throws OpenDataException {
        CompositeType ct = listEndpointsCompositeType();
        return new TabularType("listEndpoints", "Lists all the endpoints in the registry", ct, new String[]{"url"});
    }

    public static CompositeType listEndpointsCompositeType() throws OpenDataException {
        return new CompositeType("url", "Endpoints", new String[]{"url"},
                new String[]{"Url"},
                new OpenType[]{SimpleType.STRING});
    }

    public static TabularType explainEndpointTabularType() throws OpenDataException {
        CompositeType ct = explainEndpointsCompositeType();
        return new TabularType("explainEndpoint", "Explain how this endpoint is configured", ct, new String[]{"option", "kind", "type", "java type", "value", "default value", "description"});
    }

    public static CompositeType explainEndpointsCompositeType() throws OpenDataException {
        return new CompositeType("endpoint", "Explain Endpoint", new String[]{"option", "kind", "type", "java type", "value", "default value", "description"},
                new String[]{"Option", "Kind", "Type", "Java Type", "Value", "Default Value", "Description"},
                new OpenType[]{SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING});
    }

    public static TabularType listComponentsTabularType() throws OpenDataException {
        CompositeType ct = listComponentsCompositeType();
        return new TabularType("listComponents", "Lists all the components", ct, new String[]{"name", "description", "label", "status", "type", "groupId", "artifactId", "version"});
    }

    public static CompositeType listComponentsCompositeType() throws OpenDataException {
        return new CompositeType("name", "Components", new String[]{"name", "description", "label", "status", "type", "groupId", "artifactId", "version"},
                new String[]{"Name", "Description", "Label", "Status", "Type", "GroupId", "ArtifactId", "Version"},
                new OpenType[]{SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING});
    }


}
