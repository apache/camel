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
package org.apache.camel.swagger.component;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

public class SwaggerComponent extends UriEndpointComponent {

    private String componentName = "http";
    private String schema;

    public SwaggerComponent() {
        super(SwaggerEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SwaggerEndpoint endpoint = new SwaggerEndpoint(uri, this);
        endpoint.setComponentName(componentName);

        String schema;
        String verb;
        String path;
        String[] parts = remaining.split(":");
        if (parts.length == 2) {
            schema = this.schema;
            verb = parts[0];
            path = parts[1];
        } else if (parts.length == 3) {
            schema = parts[0];
            verb = parts[1];
            path = parts[2];
        } else {
            throw new IllegalArgumentException("Invalid syntax. Expected swagger:schema:verb:path?options");
        }

        endpoint.setSchema(schema);
        endpoint.setVerb(verb);
        // path must start with leading slash
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        endpoint.setPath(path);

        setProperties(endpoint, parameters);
        // any leftover parameters should be kept as additional uri parameters


        return endpoint;
    }

    public String getSchema() {
        return schema;
    }

    /**
     * The swagger schema to use in json format.
     * <p/>
     * The schema is loaded as a resource from the classpath or file system.
     */
    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getComponentName() {
        return componentName;
    }

    /**
     * The camel component to use as HTTP client for calling the REST service.
     * The default value is: http
     */
    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }
}
