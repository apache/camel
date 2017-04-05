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
package org.apache.camel.component.hystrix.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mapping settings for the hystrix component.
 */
@ConfigurationProperties(prefix = "camel.component.hystrix.mapping")
public class HystrixMappingConfiguration {

    /**
     * Endpoint for hystrix metrics servlet.
     */
    private String path = "/hystrix.stream";

    /**
     * Name of the Hystrix metrics servlet.
     */
    private String servletName = "HystrixEventStreamServlet";

    /**
     * Enables the automatic mapping of the hystrics metric servlet into the Spring web context.
     */
    private Boolean enabled = true;

    public HystrixMappingConfiguration() {
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
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
