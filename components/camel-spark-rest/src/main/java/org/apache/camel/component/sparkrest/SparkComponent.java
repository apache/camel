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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestApiConsumerFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.HostUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

public class SparkComponent extends UriEndpointComponent implements RestConsumerFactory, RestApiConsumerFactory {

    private static final Pattern PATTERN = Pattern.compile("\\{(.*?)\\}");

    @Metadata(defaultValue = "4567")
    private int port = 4567;
    @Metadata(defaultValue = "0.0.0.0")
    private String ipAddress;

    @Metadata(label = "advanced")
    private int minThreads;
    @Metadata(label = "advanced")
    private int maxThreads;
    @Metadata(label = "advanced")
    private int timeOutMillis;

    @Metadata(label = "security")
    private String keystoreFile;
    @Metadata(label = "security", secret = true)
    private String keystorePassword;
    @Metadata(label = "security")
    private String truststoreFile;
    @Metadata(label = "security", secret = true)
    private String truststorePassword;

    @Metadata(label = "advanced")
    private SparkConfiguration sparkConfiguration = new SparkConfiguration();
    @Metadata(label = "advanced")
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

    public int getMinThreads() {
        return minThreads;
    }

    /**
     * Minimum number of threads in Spark thread-pool (shared globally)
     */
    public void setMinThreads(int minThreads) {
        this.minThreads = minThreads;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    /**
     * Maximum number of threads in Spark thread-pool (shared globally)
     */
    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public int getTimeOutMillis() {
        return timeOutMillis;
    }

    /**
     * Thread idle timeout in millis where threads that has been idle for a longer period will be terminated from the thread pool
     */
    public void setTimeOutMillis(int timeOutMillis) {
        this.timeOutMillis = timeOutMillis;
    }

    public String getKeystoreFile() {
        return keystoreFile;
    }

    /**
     * Configures connection to be secure to use the keystore file
     */
    public void setKeystoreFile(String keystoreFile) {
        this.keystoreFile = keystoreFile;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    /**
     * Configures connection to be secure to use the keystore password
     */
    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public String getTruststoreFile() {
        return truststoreFile;
    }

    /**
     * Configures connection to be secure to use the truststore file
     */
    public void setTruststoreFile(String truststoreFile) {
        this.truststoreFile = truststoreFile;
    }

    public String getTruststorePassword() {
        return truststorePassword;
    }

    /**
     * Configures connection to be secure to use the truststore password
     */
    public void setTruststorePassword(String truststorePassword) {
        this.truststorePassword = truststorePassword;
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
        SparkConfiguration config = getSparkConfiguration().copy();
        setProperties(config, parameters);

        SparkEndpoint answer = new SparkEndpoint(uri, this);
        answer.setSparkConfiguration(config);
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

        if (getPort() != 4567) {
            CamelSpark.port(getPort());
        } else {
            // if no explicit port configured, then use port from rest configuration
            RestConfiguration config = getCamelContext().getRestConfiguration("spark-rest", true);
            int port = config.getPort();
            if (port > 0) {
                CamelSpark.port(port);
            }
        }

        String host = getIpAddress();
        if (host != null) {
            CamelSpark.ipAddress(host);
        } else {
            // if no explicit port configured, then use port from rest configuration
            RestConfiguration config = getCamelContext().getRestConfiguration("spark-rest", true);
            host = config.getHost();
            if (ObjectHelper.isEmpty(host)) {
                if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.allLocalIp) {
                    host = "0.0.0.0";
                } else if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.localHostName) {
                    host = HostUtils.getLocalHostName();
                } else if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.localIp) {
                    host = HostUtils.getLocalIp();
                }
            }
            CamelSpark.ipAddress(host);
        }

        if (keystoreFile != null || truststoreFile != null) {
            CamelSpark.security(keystoreFile, keystorePassword, truststoreFile, truststorePassword);
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
        CamelSpark.stop();
    }

    @Override
    public Consumer createConsumer(CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                                   String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters) throws Exception {
        return doCreateConsumer(camelContext, processor, verb, basePath, uriTemplate, consumes, produces, configuration, parameters, false);
    }

    @Override
    public Consumer createApiConsumer(CamelContext camelContext, Processor processor, String contextPath,
                                      RestConfiguration configuration, Map<String, Object> parameters) throws Exception {
        // reuse the createConsumer method we already have. The api need to use GET and match on uri prefix
        return doCreateConsumer(camelContext, processor, "get", contextPath, null, null, null, configuration, parameters, true);
    }

    Consumer doCreateConsumer(CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                              String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters, boolean api) throws Exception {

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
            config = camelContext.getRestConfiguration("spark-rest", true);
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
            Matcher matcher = PATTERN.matcher(path);
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

        String url;
        if (api) {
            url = "spark-rest:%s:%s?matchOnUriPrefix=true";
        } else {
            url = "spark-rest:%s:%s";
        }

        url = String.format(url, verb, path);

        String query = URISupport.createQueryString(map);
        if (!query.isEmpty()) {
            url = url + "?" + query;
        }

        // get the endpoint
        SparkEndpoint endpoint = camelContext.getEndpoint(url, SparkEndpoint.class);
        setProperties(camelContext, endpoint, parameters);

        // configure consumer properties
        Consumer consumer = endpoint.createConsumer(processor);
        if (config.isEnableCORS()) {
            // if CORS is enabled then configure that on the spark consumer
            if (config.getConsumerProperties() == null) {
                config.setConsumerProperties(new HashMap<String, Object>());
            }
            config.getConsumerProperties().put("enableCors", true);
        }
        if (config.getConsumerProperties() != null && !config.getConsumerProperties().isEmpty()) {
            setProperties(camelContext, consumer, config.getConsumerProperties());
        }
        return consumer;
    }
}
