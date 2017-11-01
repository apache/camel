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
package org.apache.camel.component.yql.configuration;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class YqlConfiguration {

    @UriPath(label="producer", description = "The YQL query to be sent.")
    @Metadata(required = "true")
    private String query;

    @UriParam(label="producer", enums = "json,xml", defaultValue = "json", description = "The expected format. Can only be json or xml.")
    private String format = "json";

    @UriParam(label="producer", defaultValue = "false", description = "If true, the option will be included in the HTTP request to YQL and the response will contain some diagnostics data.")
    private boolean diagnostics = false;

    @UriParam(label="producer", description = "If specified, the option will be included in the HTTP request to YQL. If the format is json, then the response will contain a JSONP callback method. "
            + "If the format is xml, then the response will contain a JSONP-X callback method. More information: https://developer.yahoo.com/yql/guide/response.html")
    private String callback = "";

    @UriParam(label = "producer", defaultValue = "true", description = "Option to disable throwing the YqlHttpException in case of failed responses from the remote server. "
            + "This allows you to get all responses regardless of the HTTP status code.")
    private boolean throwExceptionOnFailure = true;

    public String getQuery() {
        return query;
    }

    /**
     * The YQL query to be sent.
     */
    public void setQuery(final String query) {
        this.query = query;
    }

    public String getFormat() {
        return format;
    }

    /**
     * The expected format. Can only be json or xml.
     */
    public void setFormat(final String format) {
        this.format = format;
    }

    public boolean isDiagnostics() {
        return diagnostics;
    }

    /**
     * If true, the option will be included in the HTTP request to YQL and the response will contain some diagnostics data.
     */
    public void setDiagnostics(final boolean diagnostics) {
        this.diagnostics = diagnostics;
    }

    public String getCallback() {
        return callback;
    }

    /**
     * If specified, the option will be included in the HTTP request to YQL. If the format is json, then the response will contain a JSONP callback method.
     * If the format is xml, then the response will contain a JSONP-X callback method. More information: https://developer.yahoo.com/yql/guide/response.html
     */
    public void setCallback(final String callback) {
        this.callback = callback;
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    /**
     * Option to disable throwing the YqlHttpException in case of failed responses from the remote server.
     * This allows you to get all responses regardless of the HTTP status code.
     */
    public void setThrowExceptionOnFailure(final boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }
}
