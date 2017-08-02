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

import java.net.URI;
import java.util.Map;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GrpcConfiguration {

    @UriPath
    @Metadata(required = "true")
    private String host;

    @UriPath
    @Metadata(required = "true")
    private int port;
    
    @UriPath
    @Metadata(required = "true")
    private String service;
    
    @UriParam(label = "producer")
    private String method;
            
    @UriParam(label = "producer", defaultValue = "true")
    private Boolean usePlainText = true;

    @UriParam(label = "producer", defaultValue = "SIMPLE")
    private GrpcProducerStrategy producerStrategy = GrpcProducerStrategy.SIMPLE;

    @UriParam(label = "producer")
    private String streamRepliesTo;


    @UriParam(label = "consumer", defaultValue = "PROPAGATION")
    private GrpcConsumerStrategy consumerStrategy = GrpcConsumerStrategy.PROPAGATION;
    
    @UriParam(defaultValue = "false")
    private boolean forwardOnCompleted;

    @UriParam(defaultValue = "false")
    private boolean forwardOnError;

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
     * The gRPC server host name. This is localhost or 0.0.0.0 when being a
     * consumer or remote server hostname when using producer.
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
    public GrpcConsumerStrategy getConsumerStrategy() {
        return consumerStrategy;
    }

    public void setConsumerStrategy(GrpcConsumerStrategy consumerStrategy) {
        this.consumerStrategy = consumerStrategy;
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

    public GrpcProducerStrategy getProducerStrategy() {
        return producerStrategy;
    }

    /**
     * The mode used to communicate with a remote gRPC server.
     * In SIMPLE mode a single exchange is translated into a remote procedure call.
     * In STREAMING mode all exchanges will be sent within the same request (input and output of the recipient gRPC service must be of type 'stream').
     */
    public void setProducerStrategy(GrpcProducerStrategy producerStrategy) {
        this.producerStrategy = producerStrategy;
    }

    public String getStreamRepliesTo() {
        return streamRepliesTo;
    }

    /**
     * When using STREAMING client mode, it indicates the endpoint where responses should be forwarded.
     */
    public void setStreamRepliesTo(String streamRepliesTo) {
        this.streamRepliesTo = streamRepliesTo;
    }
    
    public void parseURI(URI uri, Map<String, Object> parameters, GrpcComponent component) {
        setHost(uri.getHost());
        
        if (uri.getPort() != -1) {
            setPort(uri.getPort());
        }
        
        setService(uri.getPath().substring(1));
    }
}
