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
    
    @UriParam(label = "producer")
    private String method;
    
    @UriParam
    private String host;
    
    @UriParam
    private int port;
    
    @UriParam(label = "producer")
    private String target;
    
    @UriParam(label = "producer", defaultValue = "true")
    private Boolean usePlainText = true;
    
    @UriParam(label = "consumer")
    private GrpcProcessingStrategies processingStrategy = GrpcProcessingStrategies.PROPAGATION;
    
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean forwardOnCompleted;

    @UriParam(label = "consumer", defaultValue = "false")
    private boolean forwardOnError;

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
     * The plain text connection to the server flag
     */
    public Boolean getUsePlainText() {
        return usePlainText;
    }

    public void setUsePlainText(Boolean usePlainText) {
        this.usePlainText = usePlainText;
    }

    /**
     * This option specifies the top-level strategy for processing service
     * requests and responses in streaming mode. If an aggregation strategy is
     * selected, all requests will be accumulated in the list, then transferred
     * to the flow, and the accumulated responses will be sent to the sender. If
     * a propagation strategy is selected, request is sent to the stream, and the
     * response will be immediately sent back to the sender.
     */
    public GrpcProcessingStrategies getProcessingStrategy() {
        return processingStrategy;
    }

    public void setProcessingStrategy(GrpcProcessingStrategies processingStrategy) {
        this.processingStrategy = processingStrategy;
    }

    /**
     * Determines if onCompleted events should be pushed to the Camel route.
     */
    public void setForwardOnCompleted(boolean forwardOnCompleted) {
        this.forwardOnCompleted = forwardOnCompleted;
    }

    public boolean isForwardOnCompleted() {
        return forwardOnCompleted;
    }

    /**
     * Determines if onError events should be pushed to the Camel route.
     * Exceptions will be set as message body.
     */
    public void setForwardOnError(boolean forwardOnError) {
        this.forwardOnError = forwardOnError;
    }

    public boolean isForwardOnError() {
        return forwardOnError;
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
