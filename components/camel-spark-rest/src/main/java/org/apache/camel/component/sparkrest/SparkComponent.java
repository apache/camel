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
package org.apache.camel.component.sparkrest;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import spark.SparkBase;

public class SparkComponent extends UriEndpointComponent implements RestConsumerFactory {

    private final Pattern pattern = Pattern.compile("\\{(.*?)\\}");

    private int port = SparkBase.SPARK_DEFAULT_PORT;
    private String ipAddress;
    private SparkConfiguration sparkConfiguration = new SparkConfiguration();
    private SparkBinding sparkBinding = new DefaultSparkBinding();

    public SparkComponent() {
        super(SparkEndpoint.class);
    }

    public int getPort() {
        return port;
    }

    /**
     * Port number.
     * <p/>
     * Will by default use 4567
     */
    public void setPort(int port) {
        this.port = port;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * Set the IP address that Spark should listen on. If not called the default address is '0.0.0.0'.
     */
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public SparkConfiguration getSparkConfiguration() {
        return sparkConfiguration;
    }

    /**
     * To use the shared SparkConfiguration
     */
    public void setSparkConfiguration(SparkConfiguration sparkConfiguration) {
        this.sparkConfiguration = sparkConfiguration;
    }

    public SparkBinding getSparkBinding() {
        return sparkBinding;
    }

    /**
     * To use a custom SparkBinding to map to/from Camel message.
     */
    public void setSparkBinding(SparkBinding sparkBinding) {
        this.sparkBinding = sparkBinding;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SparkEndpoint answer = new SparkEndpoint(uri, this);
        answer.setSparkConfiguration(getSparkConfiguration());
        answer.setSparkBinding(getSparkBinding());
        setProperties(answer, parameters);

        if (!remaining.contains(":")) {
            throw new IllegalArgumentException("Invalid syntax. Must be spark-rest:verb:path");
        }

        String verb = ObjectHelper.before(remaining, ":");
        String path = ObjectHelper.after(remaining, ":");

        answer.setVerb(verb);
        answer.setPath(path);

        return answer;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getPort() != SparkBase.SPARK_DEFAULT_PORT) {
            SparkBase.setPort(getPort());
        } else {
            // if no explicit port configured, then use port from rest configuration
            RestConfiguration config = getCamelContext().getRestConfiguration("spark-rest", true);
            int port = config.getPort();
            if (port > 0) {
                SparkBase.setPort(port);
            }
        }
        if (getIpAddress() != null) {
            SparkBase.setIpAddress(getIpAddress());
        }

        // configure component options
        RestConfiguration config = getCamelContext().getRestConfiguration("spark-rest", true);
        // configure additional options on spark configuration
        if (config.getComponentProperties() != null && !config.getComponentProperties().isEmpty()) {
            setProperties(sparkConfiguration, config.getComponentProperties());
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        SparkBase.stop();
    }

    @Override
    public Consumer createConsumer(CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                                   String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters) throws Exception {

        String path = basePath;
        if (uriTemplate != null) {
            // make sure to avoid double slashes
            if (uriTemplate.startsWith("/")) {
                path = path + uriTemplate;
            } else {
                path = path + "/" + uriTemplate;
            }
        }
        path = FileUtil.stripLeadingSeparator(path);

        RestConfiguration config = configuration;
        if (config == null) {
            config = getCamelContext().getRestConfiguration("spark-rest", true);
        }

        Map<String, Object> map = new HashMap<String, Object>();
        if (consumes != null) {
            map.put("accept", consumes);
        }

        // setup endpoint options
        if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
            map.putAll(config.getEndpointProperties());
        }

        if (ObjectHelper.isNotEmpty(path)) {
            // spark-rest uses :name syntax instead of {name} so we need to replace those
            Matcher matcher = pattern.matcher(path);
            path = matcher.replaceAll(":$1");
        }

        // prefix path with context-path if configured in rest-dsl configuration
        String contextPath = config.getContextPath();
        if (ObjectHelper.isNotEmpty(contextPath)) {
            contextPath = FileUtil.stripTrailingSeparator(contextPath);
            contextPath = FileUtil.stripLeadingSeparator(contextPath);
            if (ObjectHelper.isNotEmpty(contextPath)) {
                path = contextPath + "/" + path;
            }
        }

        String uri = String.format("spark-rest:%s:%s", verb, path);

        String query = URISupport.createQueryString(map);

        String url = uri;
        if (!query.isEmpty()) {
            url = url + "?" + query;
        }

        // get the endpoint
        SparkEndpoint endpoint = camelContext.getEndpoint(url, SparkEndpoint.class);
        setProperties(endpoint, parameters);

        // configure consumer properties
        Consumer consumer = endpoint.createConsumer(processor);
        if (config.getConsumerProperties() != null && !config.getConsumerProperties().isEmpty()) {
            setProperties(consumer, config.getConsumerProperties());
        }

        return consumer;
    }
}
