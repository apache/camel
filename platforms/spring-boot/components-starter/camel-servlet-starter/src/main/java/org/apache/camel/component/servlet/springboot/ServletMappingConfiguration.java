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
package org.apache.camel.component.servlet.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mapping settings for the servlet component.
 */
@ConfigurationProperties(prefix = "camel.component.servlet.mapping")
public class ServletMappingConfiguration {

    /**
     * Context path used by the servlet component for automatic mapping.
     */
    private String contextPath = "/camel/*";

    /**
     * The name of the Camel servlet.
     */
    private String servletName = "CamelServlet";

    /**
     * Enables the automatic mapping of the servlet component into the Spring web context.
     */
    private Boolean enabled = true;

    public ServletMappingConfiguration() {
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public String getServletName() {
        return servletName;
    }

    public void setServletName(String servletName) {
        this.servletName = servletName;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
