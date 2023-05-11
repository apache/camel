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
package org.apache.camel.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

public class BeanConfig {
    public static final String DEFAULT_MEDIA_TYPE = "application/json";

    String[] schemes;
    String title;
    String version;
    String licenseUrl;
    String license;

    Info info;
    String host;
    String basePath;
    String defaultConsumes = DEFAULT_MEDIA_TYPE;
    String defaultProduces = DEFAULT_MEDIA_TYPE;

    public String[] getSchemes() {
        return schemes;
    }

    public void setSchemes(String[] schemes) {
        this.schemes = schemes;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getLicenseUrl() {
        return this.licenseUrl;
    }

    public void setLicenseUrl(String licenseUrl) {
        this.licenseUrl = licenseUrl;
    }

    public String getLicense() {
        return this.license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public Info getInfo() {
        return info;
    }

    public void setInfo(Info info) {
        this.info = info;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        if (basePath != null && !basePath.isEmpty()) {
            if (!basePath.startsWith("/")) {
                this.basePath = "/" + basePath;
            } else {
                this.basePath = basePath;
            }
        }
    }

    public String getDefaultConsumes() {
        return defaultConsumes;
    }

    public void setDefaultConsumes(String defaultConsumes) {
        this.defaultConsumes = defaultConsumes;
    }

    public String getDefaultProduces() {
        return defaultProduces;
    }

    public void setDefaultProduces(String defaultProduces) {
        this.defaultProduces = defaultProduces;
    }

    public OpenAPI configure(OpenAPI openApi) {
        if (info != null) {
            openApi.setInfo(info);
        }
        for (String scheme : this.schemes) {
            Server server = new Server().url(scheme + "://" + this.host + this.basePath);
            openApi.addServersItem(server);
        }
        return openApi;
    }

    public boolean isOpenApi3() {
        return this.version == null || this.version.startsWith("3");
    }

}
