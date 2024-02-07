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

import java.net.UnknownHostException;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.HostUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriParams
public class SplunkHECConfiguration {
    private static final transient Logger LOG = LoggerFactory.getLogger(SplunkHECConfiguration.class);

    @UriParam(defaultValue = "camel")
    private String index = "camel";
    @UriParam(defaultValue = "camel")
    private String sourceType = "camel";
    @UriParam(defaultValue = "camel")
    private String source = "camel";
    @UriParam
    private String host;
    @UriParam(defaultValue = "/services/collector/event")
    private String splunkEndpoint = "/services/collector/event";
    @UriParam(label = "security", secret = true)
    @Metadata(required = true)
    private String token;
    @UriParam(label = "security")
    private boolean skipTlsVerify;
    @UriParam(label = "security", defaultValue = "true")
    private boolean https = true;
    @UriParam
    private boolean bodyOnly;
    @UriParam
    private boolean headersOnly;
    @UriParam
    private Long time;

    public String getSourceType() {
        return sourceType;
    }

    /**
     * Splunk sourcetype argument
     */
    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSource() {
        return source;
    }

    /**
     * Splunk source argument
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * Splunk index to write to
     */
    public void setIndex(String index) {
        this.index = index;
    }

    public String getIndex() {
        return index;
    }

    public String getHost() {
        if (host == null) {
            try {
                host = HostUtils.getLocalHostName();
            } catch (UnknownHostException e) {
                LOG.warn(e.getMessage(), e);
                host = "unknown";
            }
        }
        return host;
    }

    /**
     * Splunk host field of the event message. This is not the Splunk host to connect to.
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Splunk endpoint Defaults to /services/collector/event To write RAW data like JSON use /services/collector/raw For
     * a list of all endpoints refer to splunk documentation (HTTP Event Collector REST API endpoints) Example for Spunk
     * 8.2.x: https://docs.splunk.com/Documentation/SplunkCloud/8.2.2203/Data/HECRESTendpoints
     *
     * To extract timestamps in Splunk>8.0 /services/collector/event?auto_extract_timestamp=true Remember to utilize
     * RAW{} for questionmarks or slashes in parameters.
     */
    public void setSplunkEndpoint(String splunkEndpoint) {
        this.splunkEndpoint = splunkEndpoint;
    }

    public String getSplunkEndpoint() {
        return this.splunkEndpoint;
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

    public boolean isSkipTlsVerify() {
        return skipTlsVerify;
    }

    /**
     * Splunk HEC TLS verification.
     */
    public void setSkipTlsVerify(boolean skipTlsVerify) {
        this.skipTlsVerify = skipTlsVerify;
    }

    public boolean isHttps() {
        return https;
    }

    /**
     * Contact HEC over https.
     */
    public void setHttps(boolean https) {
        this.https = https;
    }

    /**
     * Send only the message body
     */
    public boolean isBodyOnly() {
        return bodyOnly;
    }

    public void setBodyOnly(boolean bodyOnly) {
        this.bodyOnly = bodyOnly;
    }

    /**
     * Send only message headers
     */
    public boolean isHeadersOnly() {
        return headersOnly;
    }

    public void setHeadersOnly(boolean headersOnly) {
        this.headersOnly = headersOnly;
    }

    /**
     * Time this even occurred. By default, the time will be when this event hits the splunk server.
     */
    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

}
