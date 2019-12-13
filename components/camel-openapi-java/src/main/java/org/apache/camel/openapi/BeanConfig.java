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

import java.util.ArrayList;

import io.apicurio.datamodels.core.models.common.Info;
import io.apicurio.datamodels.core.models.common.Server;
import io.apicurio.datamodels.openapi.models.OasDocument;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v3.models.Oas30Document;


public class BeanConfig {
    String resourcePackage;
    String[] schemes;
    String title;
    String version;
    String description;
    String termsOfServiceUrl;
    String contact;
    String license;
    String licenseUrl;
    String filterClass;

    Info info;
    String host;
    String basePath;

    String scannerId;
    String configId;
    String contextId;

    boolean expandSuperTypes = true;

    private boolean usePathBasedConfig = false;

    public boolean isUsePathBasedConfig() {
        return usePathBasedConfig;
    }

    public void setUsePathBasedConfig(boolean usePathBasedConfig) {
        this.usePathBasedConfig = usePathBasedConfig;
    }

    public String getResourcePackage() {
        return this.resourcePackage;
    }

    public void setResourcePackage(String resourcePackage) {
        this.resourcePackage = resourcePackage;
    }

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTermsOfServiceUrl() {
        return termsOfServiceUrl;
    }

    public void setTermsOfServiceUrl(String termsOfServiceUrl) {
        this.termsOfServiceUrl = termsOfServiceUrl;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public String getLicenseUrl() {
        return licenseUrl;
    }

    public void setLicenseUrl(String licenseUrl) {
        this.licenseUrl = licenseUrl;
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

    
    
    public String getContextId() {
        return contextId;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
    }

    public String getScannerId() {
        return scannerId;
    }

    public void setScannerId(String scannerId) {
        this.scannerId = scannerId;
    }

    public String getConfigId() {
        return configId;
    }

    
    public void setConfigId(String configId) {
        this.configId = configId;
    }

    public String getBasePath() {
        return basePath;
    }

    public boolean getExpandSuperTypes() {
        return expandSuperTypes;
    }

    public void setExpandSuperTypes(boolean expandSuperTypes) {
        this.expandSuperTypes = expandSuperTypes;
    }

    public void setBasePath(String basePath) {
        if (!"".equals(basePath) && basePath != null) {
            if (!basePath.startsWith("/")) {
                this.basePath = "/" + basePath;
            } else {
                this.basePath = basePath;
            }
        }
    }
    
    public OasDocument configure(OasDocument openApi) {
        if (openApi instanceof Oas20Document) {
            configureOas20((Oas20Document)openApi);
        } else if (openApi instanceof Oas30Document) {
            configureOas30((Oas30Document)openApi);
        }
        return openApi;
    }

    private void configureOas30(Oas30Document openApi) {
        openApi.info = info;
        Server server = openApi.createServer();
        String serverUrl = new StringBuilder().append(this.schemes[0]).append("://").append(this.host).append(this.basePath).toString();
        server.url = serverUrl;
        openApi.addServer(server);
    }

    private void configureOas20(Oas20Document openApi) {
        if (schemes != null) {
            if (openApi.schemes == null) {
                openApi.schemes = new ArrayList<String>();
            }
            for (String scheme : schemes) {
                openApi.schemes.add(scheme);
            }
        }
        openApi.info = info;
        openApi.host = host;
        openApi.basePath = basePath;
    }
    
    public boolean isOpenApi3() {
        return this.version == null || this.version.startsWith("3");
    }

}
