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

/**
 * YQL configuration that should reflect https://developer.yahoo.com/yql/guide/users-overview.html
 */
@UriParams
public class YqlConfiguration {

    @UriPath
    @Metadata(required = "true")
    private String query;

    @UriParam(enums = "json,xml", defaultValue = "json")
    private String format = "json";

    @UriParam
    private String callback;

    @UriParam
    private String crossProduct;

    @UriParam
    private boolean diagnostics;

    @UriParam
    private boolean debug;

    @UriParam
    private String env;

    @UriParam
    private String jsonCompat;

    @UriParam(defaultValue = "true")
    private boolean throwExceptionOnFailure = true;

    @UriParam(label = "security", defaultValue = "true")
    private boolean https = true;

    public String getQuery() {
        return query;
    }

    /**
     * The YQL statement to execute.
     */
    public void setQuery(final String query) {
        this.query = query;
    }

    public String getFormat() {
        return format;
    }

    /**
     * The expected format. Allowed values: xml or json.
     */
    public void setFormat(final String format) {
        this.format = format;
    }

    public String getCallback() {
        return callback;
    }

    /**
     * The name of the JavaScript callback function for JSONP format. If callback is set and if format=json, then the response format is JSON. For more
     * information on using XML instead of JSON, see JSONP-X. https://developer.yahoo.com/yql/guide/response.html
     */
    public void setCallback(final String callback) {
        this.callback = callback;
    }

    public String getCrossProduct() {
        return crossProduct;
    }

    /**
     * When given the value optimized, the projected fields in SELECT statements that may be returned in separate item elements in the response are optimized to be in a single item element instead.
     * The only allowed value is optimized. More information https://developer.yahoo.com/yql/guide/response.html#response-optimizing=
     */
    public void setCrossProduct(final String crossProduct) {
        this.crossProduct = crossProduct;
    }

    public boolean isDiagnostics() {
        return diagnostics;
    }

    /**
     * If true, diagnostic information is returned with the response.
     */
    public void setDiagnostics(final boolean diagnostics) {
        this.diagnostics = diagnostics;
    }

    public boolean isDebug() {
        return debug;
    }

    /**
     * If true, and if diagnostic is set to true, debug data is returned with the response.
     * More information: https://developer.yahoo.com/yql/guide/dev-external_tables.html#odt-enable-logging=
     */
    public void setDebug(final boolean debug) {
        this.debug = debug;
    }

    public String getEnv() {
        return env;
    }

    /**
     * Allows you to use multiple Open Data Tables through a YQL environment file.
     * More information https://developer.yahoo.com/yql/guide/yql_storage.html#using-records-env-files=
     */
    public void setEnv(final String env) {
        this.env = env;
    }

    public String getJsonCompat() {
        return jsonCompat;
    }

    /**
     * Enables lossless JSON processing. The only allowed value is new.
     * More information https://developer.yahoo.com/yql/guide/response.html#json-to-json
     */
    public void setJsonCompat(final String jsonCompat) {
        this.jsonCompat = jsonCompat;
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

    public boolean isHttps() {
        return https;
    }

    /**
     * Option to use HTTPS to communicate with YQL.
     */
    public void setHttps(final boolean https) {
        this.https = https;
    }
}
