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
package org.apache.camel.zipkin.starter;

import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camel.zipkin")
public class ZipkinConfigurationProperties {
    /**
     * Sets the POST URL for zipkin's <a href="http://zipkin.io/zipkin-api/#/">v2 api</a>, usually
     * "http://zipkinhost:9411/api/v2/spans"
     */
    private String endpoint;

    /**
     * Sets the hostname if sending spans to a remote zipkin scribe (thrift RPC) collector.
     */
    private String hostName;

    /**
     * Sets the port if sending spans to a remote zipkin scribe (thrift RPC) collector.
     */
    private int port;

    /**
     * Configures a rate that decides how many events should be traced by zipkin.
     * The rate is expressed as a percentage (1.0f = 100%, 0.5f is 50%, 0.1f is 10%).
     */
    private float rate = 1.0f;

    /**
     * Whether to include the Camel message body in the zipkin traces.
     *
     * This is not recommended for production usage, or when having big payloads.
     * You can limit the size by configuring camel.springboot.log-debug-max-chars option.
     */
    private boolean includeMessageBody;

    /**
     * Whether to include message bodies that are stream based in the zipkin traces.
     *
     * This is not recommended for production usage, or when having big payloads.
     * You can limit the size by configuring camel.springboot.log-debug-max-chars option.
     */
    private boolean includeMessageBodyStreams;

    /**
     * To use a global service name that matches all Camel events
     */
    private String serviceName;

    /**
     * Sets exclude pattern(s) that will disable tracing with zipkin for Camel messages that matches the pattern.
     */
    private Set<String> excludePatterns;

    /**
     * Sets client service mapping(s) that matches Camel events to the given zipkin service name.
     * The key is the pattern, the value is the service name.
     */
    private Map<String, String> clientServiceMappings;

    /**
     * Sets server service mapping(s) that matches Camel events to the given zipkin service name.
     * The key is the pattern, the value is the service name.
     */
    private Map<String, String> serverServiceMappings;

    // Getters & setters

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public float getRate() {
        return rate;
    }

    public void setRate(float rate) {
        this.rate = rate;
    }

    public boolean isIncludeMessageBody() {
        return includeMessageBody;
    }

    public void setIncludeMessageBody(boolean includeMessageBody) {
        this.includeMessageBody = includeMessageBody;
    }

    public boolean isIncludeMessageBodyStreams() {
        return includeMessageBodyStreams;
    }

    public void setIncludeMessageBodyStreams(boolean includeMessageBodyStreams) {
        this.includeMessageBodyStreams = includeMessageBodyStreams;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Set<String> getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(Set<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    public Map<String, String> getClientServiceMappings() {
        return clientServiceMappings;
    }

    public void setClientServiceMappings(Map<String, String> clientServiceMappings) {
        this.clientServiceMappings = clientServiceMappings;
    }

    public Map<String, String> getServerServiceMappings() {
        return serverServiceMappings;
    }

    public void setServerServiceMappings(Map<String, String> serverServiceMappings) {
        this.serverServiceMappings = serverServiceMappings;
    }
}
