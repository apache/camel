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
package org.apache.camel.component.rest;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * REST-DSL component.
 */
public class RestComponent extends UriEndpointComponent {

    private String componentName;
    private String apiDoc;
    private String host;

    public RestComponent() {
        super(RestEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        RestEndpoint answer = new RestEndpoint(uri, this);
        answer.setComponentName(componentName);
        answer.setApiDoc(apiDoc);

        // if no explicit host was given, then fallback and use default configured host
        String h = resolveAndRemoveReferenceParameter(parameters, "host", String.class, host);
        if (h == null && getCamelContext().getRestConfiguration() != null) {
            h = getCamelContext().getRestConfiguration().getHost();
        }
        // host must start with http:// or https://
        if (h != null && !(h.startsWith("http://") || h.startsWith("https://"))) {
            h = "http://" + h;
        }
        answer.setHost(h);

        setProperties(answer, parameters);
        answer.setParameters(parameters);
        // the rest is URI parameters on path
        String query = URISupport.createQueryString(parameters);
        if (ObjectHelper.isNotEmpty(query)) {
            answer.setQueryParameters(query);
        }

        if (!remaining.contains(":")) {
            throw new IllegalArgumentException("Invalid syntax. Must be rest:method:path[:uriTemplate] where uriTemplate is optional");
        }

        String method = ObjectHelper.before(remaining, ":");
        String s = ObjectHelper.after(remaining, ":");

        String path;
        String uriTemplate;
        if (s != null && s.contains(":")) {
            path = ObjectHelper.before(s, ":");
            uriTemplate = ObjectHelper.after(s, ":");
        } else {
            path = s;
            uriTemplate = null;
        }

        // remove trailing slashes
        path = FileUtil.stripTrailingSeparator(path);
        uriTemplate = FileUtil.stripTrailingSeparator(uriTemplate);

        answer.setMethod(method);
        answer.setPath(path);
        answer.setUriTemplate(uriTemplate);

        // if no explicit component name was given, then fallback and use default configured component name
        if (answer.getComponentName() == null && getCamelContext().getRestConfiguration() != null) {
            answer.setComponentName(getCamelContext().getRestConfiguration().getComponent());
        }

        return answer;
    }

    public String getComponentName() {
        return componentName;
    }

    /**
     * The Camel Rest component to use for the REST transport, such as restlet, spark-rest.
     * If no component has been explicit configured, then Camel will lookup if there is a Camel component
     * that integrates with the Rest DSL, or if a org.apache.camel.spi.RestConsumerFactory (consumer)
     * or org.apache.camel.spi.RestProducerFactory (producer) is registered in the registry.
     * If either one is found, then that is being used.
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
