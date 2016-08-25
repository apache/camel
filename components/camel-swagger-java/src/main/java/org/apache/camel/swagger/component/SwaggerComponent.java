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
import org.apache.camel.util.URISupport;

@Deprecated
public class SwaggerComponent extends UriEndpointComponent {

    private String componentName = "http";
    private String apiDoc;
    private String host;

    // TODO: we could move this to rest component in camel-core
    // and have its producer support using a swagger schema and use a factory to lookup
    // the code in this component that creates the producer

    public SwaggerComponent() {
        super(SwaggerEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SwaggerEndpoint endpoint = new SwaggerEndpoint(uri, this);
        endpoint.setComponentName(componentName);
        endpoint.setApiDoc(apiDoc);
        endpoint.setHost(host);

        String verb;
        String path;
        String[] parts = remaining.split(":");
        if (parts.length == 2) {
            verb = parts[0];
            path = parts[1];
        } else {
            throw new IllegalArgumentException("Invalid syntax. Expected swagger:verb:path?options");
        }

        endpoint.setVerb(verb);
        // path must start with leading slash
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        endpoint.setPath(path);

        setProperties(endpoint, parameters);

        // the rest is URI parameters on path
        String query = URISupport.createQueryString(parameters);
        endpoint.setQueryParameters(query);

        return endpoint;
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

    public String getApiDoc() {
        return apiDoc;
    }

    /**
     * The swagger api doc resource to use.
     * The resource is loaded from classpath by default and must be in JSon format.
     */
    public void setApiDoc(String apiDoc) {
        this.apiDoc = apiDoc;
    }

    public String getHost() {
        return host;
    }

    /**
     * Host and port of HTTP service to use (override host in swagger schema)
     */
    public void setHost(String host) {
        this.host = host;
    }
}
