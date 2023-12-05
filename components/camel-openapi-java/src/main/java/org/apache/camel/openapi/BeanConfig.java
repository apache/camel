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

import java.util.Map;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import org.apache.camel.tooling.util.Version;

public class BeanConfig {
    public static final String DEFAULT_MEDIA_TYPE = "application/json";
    public static final Version OPENAPI_VERSION_30 = new Version("3.0.0");
    public static final Version OPENAPI_VERSION_31 = new Version("3.1.0");

    String[] schemes;
    String title;
    Version version = new Version("3.0.1");
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
        return version.toString();
    }

    public void setVersion(String version) {
        this.version = new Version(version);
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
        if (isOpenApi31()) {
            // This is a workaround to addType on ComposedSchema
            // It should be removed if https://github.com/swagger-api/swagger-core/issues/4574 resolved
            if (openApi.getComponents() != null) {
                Map<String, Schema> schemas = openApi.getComponents().getSchemas();
                if (schemas != null) {
                    for (Schema schema : schemas.values()) {
                        if (schema instanceof ComposedSchema) {
                            String type = schema.getType();
                            if (type != null) {
                                schema.addType(type);
                            }
                        }
                    }
                }
            }
        }
        return openApi;
    }

    public boolean isOpenApi2() {
        return version.compareTo(OPENAPI_VERSION_30) < 0;
    }

    public boolean isOpenApi30() {
        return version.compareTo(OPENAPI_VERSION_30) >= 0 && version.compareTo(OPENAPI_VERSION_31) < 0;
    }

    public boolean isOpenApi31() {
        return version.compareTo(OPENAPI_VERSION_31) >= 0;
    }

}
