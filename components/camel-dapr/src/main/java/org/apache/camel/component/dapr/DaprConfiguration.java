/*
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
package org.apache.camel.component.dapr;

import io.dapr.client.domain.HttpExtension;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class DaprConfiguration implements Cloneable {

    @UriPath(label = "producer", enums = "invokeService")
    @Metadata(required = true)
    private DaprOperation operation;
    @UriParam(label = "producer", description = "Target service to invoke. Can be a Dapr App ID, a named HTTPEndpoint, " +
                                                "or a FQDN/public URL")
    private String serviceToInvoke;
    @UriParam(label = "producer", description = "The name of the method or route to invoke on the target service")
    private String methodToInvoke;
    @UriParam(label = "producer", description = "The HTTP verb to use for invoking the method", defaultValue = "POST")
    private String verb = "POST";
    @UriParam(label = "producer",
              description = "HTTP method to use when invoking the service. Accepts verbs like GET, POST, PUT, DELETE, etc. "
                            + "Creates a minimal HttpExtension with no headers or query params. Takes precedence over verb")
    @Metadata(autowired = true)
    private HttpExtension httpExtension;

    /**
     * The Dapr <b>building block operation</b> to perform with this component
     */
    public DaprOperation getOperation() {
        return operation;
    }

    public void setOperation(DaprOperation operation) {
        this.operation = operation;
    }

    /**
     * The <b>target service</b> to invoke.
     * <p>
     * This can be one of the following:
     * <ul>
     * <li>A Dapr App ID</li>
     * <li>A named HTTPEndpoint resource</li>
     * <li>A fully qualified domain name (FQDN) or public URL</li>
     * </ul>
     */
    public String getServiceToInvoke() {
        return serviceToInvoke;
    }

    public void setServiceToInvoke(String serviceToInvoke) {
        this.serviceToInvoke = serviceToInvoke;
    }

    /**
     * The <b>method or route</b> to invoke on the target service.
     * <p>
     * This defines the specific method or endpoint to invoke on the target service, such as a method name or route
     * path.
     */
    public String getMethodToInvoke() {
        return methodToInvoke;
    }

    public void setMethodToInvoke(String methodToInvoke) {
        this.methodToInvoke = methodToInvoke;
    }

    /**
     * The <b>HTTP verb</b> to use for service invocation.
     * <p>
     * This defines the type of HTTP request to send when invoking the service method. Defaults to POST.
     */
    public String getVerb() {
        return verb;
    }

    public void setVerb(String verb) {
        this.verb = verb;
    }

    /**
     * The <b>HttpExtension</b> to use for service invocation.
     * <p>
     * Minimal HttpExtension object without query parameters and headers. Takes precedence over defined verb, query
     * parameter and headers.
     */
    public HttpExtension getHttpExtension() {
        return httpExtension;
    }

    public void setHttpExtension(HttpExtension httpExtension) {
        this.httpExtension = httpExtension;
    }

    public DaprConfiguration copy() {
        try {
            return (DaprConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
