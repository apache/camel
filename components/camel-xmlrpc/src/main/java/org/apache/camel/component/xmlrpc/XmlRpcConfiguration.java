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
    @UriParam
    private java.util.TimeZone timeZone;
    @UriParam
    private boolean gzipCompressing;
    @UriParam
    private boolean gzipRequesting;
    @UriParam
    private String basicUserName;
    @UriParam
    private String basicPassword;
    @UriParam
    private int connectionTimeout;
    @UriParam
    private int replyTimeout;
    @UriParam
    private boolean enabledForExceptions;
    @UriParam
    private org.apache.xmlrpc.common.XmlRpcRequestProcessor xmlRpcServer;
    @UriParam
    private String userAgent;

    public boolean isEnabledForExtensions() {
        return enabledForExtensions;
    }

    public void setEnabledForExtensions(boolean enabledForExtensions) {
        this.enabledForExtensions = enabledForExtensions;
    }

    public boolean isContentLengthOptional() {
        return contentLengthOptional;
    }

    public void setContentLengthOptional(boolean contentLengthOptional) {
        this.contentLengthOptional = contentLengthOptional;
    }

    public String getBasicEncoding() {
        return basicEncoding;
    }

    public void setBasicEncoding(String basicEncoding) {
        this.basicEncoding = basicEncoding;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    public boolean isGzipCompressing() {
        return gzipCompressing;
    }

    public void setGzipCompressing(boolean gzipCompressing) {
        this.gzipCompressing = gzipCompressing;
    }

    public boolean isGzipRequesting() {
        return gzipRequesting;
    }

    public void setGzipRequesting(boolean gzipRequesting) {
        this.gzipRequesting = gzipRequesting;
    }

    public String getBasicUserName() {
        return basicUserName;
    }

    public void setBasicUserName(String basicUserName) {
        this.basicUserName = basicUserName;
    }

    public String getBasicPassword() {
        return basicPassword;
    }

    public void setBasicPassword(String basicPassword) {
        this.basicPassword = basicPassword;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getReplyTimeout() {
        return replyTimeout;
    }

    public void setReplyTimeout(int replyTimeout) {
        this.replyTimeout = replyTimeout;
    }

    public boolean isEnabledForExceptions() {
        return enabledForExceptions;
    }

    public void setEnabledForExceptions(boolean enabledForExceptions) {
        this.enabledForExceptions = enabledForExceptions;
    }

    public XmlRpcRequestProcessor getXmlRpcServer() {
        return xmlRpcServer;
    }

    public void setXmlRpcServer(XmlRpcRequestProcessor xmlRpcServer) {
        this.xmlRpcServer = xmlRpcServer;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
