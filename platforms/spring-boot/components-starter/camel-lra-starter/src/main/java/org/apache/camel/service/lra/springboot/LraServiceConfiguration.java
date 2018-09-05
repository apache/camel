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
package org.apache.camel.service.lra.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import static org.apache.camel.service.lra.LRAConstants.DEFAULT_COORDINATOR_CONTEXT_PATH;
import static org.apache.camel.service.lra.LRAConstants.DEFAULT_LOCAL_PARTICIPANT_CONTEXT_PATH;

/**
 * Spring-boot Auto-configuration for LRA service.
 */
@ConfigurationProperties(prefix = "camel.service.lra")
public class LraServiceConfiguration {

    /**
     * Global option to enable/disable component auto-configuration, default is true.
     */
    private boolean enabled = true;

    /**
     * The base URL of the LRA coordinator service (e.g. http://lra-host:8080)
     */
    private String coordinatorUrl;

    /**
     * The context path of the LRA coordinator service
     */
    private String coordinatorContextPath = DEFAULT_COORDINATOR_CONTEXT_PATH;

    /**
     * The local URL where the coordinator should send callbacks to (e.g. http://my-host-name:8080)
     */
    private String localParticipantUrl;

    /**
     * The context path of the local participant callback services
     */
    private String localParticipantContextPath = DEFAULT_LOCAL_PARTICIPANT_CONTEXT_PATH;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCoordinatorUrl() {
        return coordinatorUrl;
    }

    public void setCoordinatorUrl(String coordinatorUrl) {
        this.coordinatorUrl = coordinatorUrl;
    }

    public String getCoordinatorContextPath() {
        return coordinatorContextPath;
    }

    public void setCoordinatorContextPath(String coordinatorContextPath) {
        this.coordinatorContextPath = coordinatorContextPath;
    }

    public String getLocalParticipantUrl() {
        return localParticipantUrl;
    }

    public void setLocalParticipantUrl(String localParticipantUrl) {
        this.localParticipantUrl = localParticipantUrl;
    }

    public String getLocalParticipantContextPath() {
        return localParticipantContextPath;
    }

    public void setLocalParticipantContextPath(String localParticipantContextPath) {
        this.localParticipantContextPath = localParticipantContextPath;
    }
}