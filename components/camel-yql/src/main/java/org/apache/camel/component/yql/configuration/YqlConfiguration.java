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

    @UriPath
    @Metadata(required = "true")
    private String query;

    @UriParam
    private String format;

    @UriParam
    private boolean diagnostics;

    @UriParam
    private String callback;

    public String getQuery() {
        return query;
    }

    /**
     * Set the YQL query
     */
    public void setQuery(final String query) {
        this.query = query;
    }

    public String getFormat() {
        return format;
    }

    /**
     * Set the YQL format, xml or json
     */
    public void setFormat(final String format) {
        this.format = format;
    }

    public boolean isDiagnostics() {
        return diagnostics;
    }

    /**
     * Set if diagnostics should be included in the query
     */
    public void setDiagnostics(final boolean diagnostics) {
        this.diagnostics = diagnostics;
    }

    public String getCallback() {
        return callback;
    }

    /**
     * Set the callback function
     */
    public void setCallback(final String callback) {
        this.callback = callback;
    }
}
