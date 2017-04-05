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
package org.apache.camel.component.grpc;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GrpcConfiguration {

    @UriPath
    @Metadata(required = "true")
    private String service;
    @UriParam
    private String method;
    @UriParam
    private String host;
    @UriParam
    private int port;
    @UriParam
    private String target;
    @UriParam(defaultValue = "true")
    private Boolean usePlainText = true;

    private String serviceName;
    private String servicePackage;

    /**
     * Fully qualified service name from the protocol buffer descriptor file
     * (package dot service definition name) 
     */
    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    /**
     * gRPC method name
     */
    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * The gRPC server host name
     */
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    /**
     * The gRPC server port
     */
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    /**
     * The channel target name as alternative to host and port parameters
     */
    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    /**
     * The plaintext connection to the server flag
     */
    public Boolean getUsePlainText() {
        return usePlainText;
    }

    public void setUsePlainText(Boolean usePlainText) {
        this.usePlainText = usePlainText;
    }

    /**
     * The service name extracted from the full service name
     */
    protected String getServiceName() {
        return serviceName;
    }

    protected void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * The service package name extracted from the full service name
     */
    protected String getServicePackage() {
        return servicePackage;
    }

    protected void setServicePackage(String servicePackage) {
        this.servicePackage = servicePackage;
    }
}
