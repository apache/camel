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

    private String hostName;
    private int port;
    private float rate = 1.0f;
    private boolean includeMessageBody;
    private String serviceName;
    private Set<String> excludePatterns;
    private Map<String, String> serviceMappings;

    public String getHostName() {
        return hostName;
    }

    /**
     * Sets a hostname for the remote zipkin server to use.
     */
    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public int getPort() {
        return port;
    }

    /**
     * Sets the port number for the remote zipkin server to use.
     */
    public void setPort(int port) {
        this.port = port;
    }

    public float getRate() {
        return rate;
    }

    /**
     * Configures a rate that decides how many events should be traced by zipkin.
     * The rate is expressed as a percentage (1.0f = 100%, 0.5f is 50%, 0.1f is 10%).
     *
     * @param rate minimum sample rate is 0.0001, or 0.01% of traces
     */
    public void setRate(float rate) {
        this.rate = rate;
    }

    public boolean isIncludeMessageBody() {
        return includeMessageBody;
    }

    /**
     * Whether to include the Camel message body in the zipkin traces.
     * <p/>
     * This is not recommended for production usage, or when having big payloads. You can limit the size by
     * configuring the <a href="http://camel.apache.org/how-do-i-set-the-max-chars-when-debug-logging-messages-in-camel.html">max debug log size</a>.
     */
    public void setIncludeMessageBody(boolean includeMessageBody) {
        this.includeMessageBody = includeMessageBody;
    }

    public String getServiceName() {
        return serviceName;
    }

    /**
     * To use a global service name that matches all Camel events
     */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Set<String> getExcludePatterns() {
        return excludePatterns;
    }

    /**
     * Sets exclude pattern(s) that will disable tracing with zipkin for Camel messages that matches the pattern.
     */
    public void setExcludePatterns(Set<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    public Map<String, String> getServiceMappings() {
        return serviceMappings;
    }

    /**
     * Sets service mapping(s) that matches Camel events to the given zipkin service name.
     * The key is the pattern, the value is the service name.
     */
    public void setServiceMappings(Map<String, String> serviceMappings) {
        this.serviceMappings = serviceMappings;
    }
}
