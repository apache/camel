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
package org.apache.camel.component.xmlrpc;

import java.util.TimeZone;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.xmlrpc.common.XmlRpcRequestProcessor;

@UriParams
public class XmlRpcConfiguration {

    @UriParam
    private boolean enabledForExtensions;
    @UriParam
    private boolean contentLengthOptional;
    @UriParam
    private String basicEncoding;
    @UriParam
    private String encoding;
    @UriParam(label = "advanced")
    private java.util.TimeZone timeZone;
    @UriParam
    private boolean gzipCompressing;
    @UriParam
    private boolean gzipRequesting;
    @UriParam(label = "security", secret = true)
    private String basicUserName;
    @UriParam(label = "security", secret = true)
    private String basicPassword;
    @UriParam
    private int connectionTimeout;
    @UriParam
    private int replyTimeout;
    @UriParam
    private boolean enabledForExceptions;
    @UriParam(label = "advanced")
    private org.apache.xmlrpc.common.XmlRpcRequestProcessor xmlRpcServer;
    @UriParam(label = "advanced")
    private String userAgent;

    public boolean isEnabledForExtensions() {
        return enabledForExtensions;
    }

    /**
     * Whether extensions are enabled. By default, the client or server is strictly compliant to the XML-RPC specification and extensions are disabled.
     */
    public void setEnabledForExtensions(boolean enabledForExtensions) {
        this.enabledForExtensions = enabledForExtensions;
    }

    public boolean isContentLengthOptional() {
        return contentLengthOptional;
    }

    /**
     * Whether a "Content-Length" header may be omitted. The XML-RPC specification demands, that such a header be present.
     */
    public void setContentLengthOptional(boolean contentLengthOptional) {
        this.contentLengthOptional = contentLengthOptional;
    }

    public String getBasicEncoding() {
        return basicEncoding;
    }

    /**
     * Sets the encoding for basic authentication, null means UTF-8 is chosen.
     */
    public void setBasicEncoding(String basicEncoding) {
        this.basicEncoding = basicEncoding;
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * Sets the requests encoding, null means UTF-8 is chosen.
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    /**
     * The timezone, which is used to interpret date/time.
     * Defaults to {@link TimeZone#getDefault()}.
     */
    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    public boolean isGzipCompressing() {
        return gzipCompressing;
    }

    /**
     * Whether gzip compression is being used for transmitting the request.
     */
    public void setGzipCompressing(boolean gzipCompressing) {
        this.gzipCompressing = gzipCompressing;
    }

    public boolean isGzipRequesting() {
        return gzipRequesting;
    }

    /**
     * Whether gzip compression is being used for transmitting the request.
     */
    public void setGzipRequesting(boolean gzipRequesting) {
        this.gzipRequesting = gzipRequesting;
    }

    public String getBasicUserName() {
        return basicUserName;
    }

    /**
     * The user name for basic authentication.
     */
    public void setBasicUserName(String basicUserName) {
        this.basicUserName = basicUserName;
    }

    public String getBasicPassword() {
        return basicPassword;
    }

    /**
     * The password for basic authentication.
     */
    public void setBasicPassword(String basicPassword) {
        this.basicPassword = basicPassword;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Set the connection timeout in milliseconds, 0 is to disable it
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getReplyTimeout() {
        return replyTimeout;
    }

    /**
     * Set the reply timeout in milliseconds, 0 is to disable it.
     */
    public void setReplyTimeout(int replyTimeout) {
        this.replyTimeout = replyTimeout;
    }

    public boolean isEnabledForExceptions() {
        return enabledForExceptions;
    }

    /**
     * Whether the response should contain a "faultCause" element in case of errors.
     * The "faultCause" is an exception, which the server has trapped and written into a byte stream as a serializable object.
     */
    public void setEnabledForExceptions(boolean enabledForExceptions) {
        this.enabledForExceptions = enabledForExceptions;
    }

    public XmlRpcRequestProcessor getXmlRpcServer() {
        return xmlRpcServer;
    }

    /**
     * To use a custom XmlRpcRequestProcessor as server.
     */
    public void setXmlRpcServer(XmlRpcRequestProcessor xmlRpcServer) {
        this.xmlRpcServer = xmlRpcServer;
    }

    public String getUserAgent() {
        return userAgent;
    }

    /**
     * The http user agent header to set when doing xmlrpc requests
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
