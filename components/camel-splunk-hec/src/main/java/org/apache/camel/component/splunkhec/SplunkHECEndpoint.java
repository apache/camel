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
package org.apache.camel.component.splunkhec;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.commons.validator.routines.DomainValidator;
import org.apache.commons.validator.routines.InetAddressValidator;

/**
 * The splunk component allows to publish events in Splunk using the HTTP Event Collector.
 */
@UriEndpoint(firstVersion = "3.3.0", scheme = "splunk-hec", title = "Splunk HEC", producerOnly = true,
             syntax = "splunk-hec:splunkURL/token", category = { Category.MONITORING },
             headersClass = SplunkHECConstants.class)
public class SplunkHECEndpoint extends DefaultEndpoint {

    private static final Pattern URI_PARSER
            = Pattern.compile("splunk-hec\\:\\/?\\/?(.*?):(\\d+)/(\\w{8}-\\w{4}-\\w{4}-\\w{4}-\\w{12})\\??.*");

    @UriPath
    @Metadata(required = true)
    private String splunkURL;
    @UriPath(label = "security")
    @Metadata(required = true)
    private String token;
    @UriParam
    private SplunkHECConfiguration configuration;

    public SplunkHECEndpoint() {
    }

    public SplunkHECEndpoint(String uri, SplunkHECComponent component, SplunkHECConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
        Matcher match = URI_PARSER.matcher(uri);
        if (!match.matches()) {
            throw new IllegalArgumentException("Invalid URI: " + uri);
        }
        String hostname = match.group(1);
        int port = Integer.parseInt(match.group(2));

        if (!DomainValidator.getInstance(true).isValid(hostname)
                && !InetAddressValidator.getInstance().isValidInet4Address(hostname)) {
            throw new IllegalArgumentException("Invalid hostname: " + hostname);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
        splunkURL = hostname + ":" + port;
        token = match.group(3);
    }

    @Override
    public Producer createProducer() {
        return new SplunkHECProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException();
    }

    public SplunkHECConfiguration getConfiguration() {
        return configuration;
    }

    public String getSplunkURL() {
        return splunkURL;
    }

    /**
     * Splunk Host and Port (example: my_splunk_server:8089)
     */
    public void setSplunkURL(String splunkURL) {
        this.splunkURL = splunkURL;
    }

    public String getToken() {
        return token;
    }

    /**
     * Splunk HEC token (this is the token created for HEC and not the user's token)
     */
    public void setToken(String token) {
        this.token = token;
    }

}
