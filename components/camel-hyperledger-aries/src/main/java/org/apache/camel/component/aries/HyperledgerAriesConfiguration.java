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
package org.apache.camel.component.aries;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class HyperledgerAriesConfiguration {

    @UriPath(description = "The wallet to connect to")
    @Metadata(required = true)
    private String walletName;
    @UriParam(description = "An API path (e.g. /issue-credential/records)")
    @Metadata(required = false)
    private String service;
    @UriParam(description = "A schema name")
    @Metadata(required = false)
    private String schemaName;
    @UriParam(description = "A schema version")
    @Metadata(required = false)
    private String schemaVersion;
    @UriParam(description = "Allow on-demand schema creation")
    @Metadata(required = false)
    private boolean autoSchema;

    public String getWalletName() {
        return walletName;
    }

    public void setWalletName(String name) {
        this.walletName = name;
    }

    public String getService() {
        return service;
    }

    public void setService(String path) {
        this.service = path;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public boolean isAutoSchema() {
        return autoSchema;
    }

    public void setAutoSchema(boolean autoSchema) {
        this.autoSchema = autoSchema;
    }
}
