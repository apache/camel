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
    @UriParam(label = "security")
    private boolean skipTlsVerify;
    @UriParam(label = "security", defaultValue = "true")
    private boolean https = true;

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
     * Splunk host.
     */
    public void setHost(String host) {
        this.host = host;
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
}
