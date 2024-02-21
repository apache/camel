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
package org.apache.camel.main;

import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;

/**
 * Global configuration for Saga LRA
 */
@Configurer(bootstrap = true)
public class LraConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    @Metadata(defaultValue = "false")
    private boolean enabled;
    private String coordinatorUrl;
    @Metadata(defaultValue = "/lra-coordinator")
    private String coordinatorContextPath = "/lra-coordinator";
    private String localParticipantUrl;
    @Metadata(defaultValue = "/lra-participant")
    private String localParticipantContextPath = "/lra-participant";

    public LraConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * To enable Saga LRA
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCoordinatorUrl() {
        return coordinatorUrl;
    }

    /**
     * The URL for the LRA coordinator service that orchestrates the transactions
     */
    public void setCoordinatorUrl(String coordinatorUrl) {
        this.coordinatorUrl = coordinatorUrl;
    }

    public String getCoordinatorContextPath() {
        return coordinatorContextPath;
    }

    /**
     * The context-path for the LRA coordinator.
     *
     * Is default /lra-coordinator
     */
    public void setCoordinatorContextPath(String coordinatorContextPath) {
        this.coordinatorContextPath = coordinatorContextPath;
    }

    public String getLocalParticipantUrl() {
        return localParticipantUrl;
    }

    /**
     * The URL for the local participant
     */
    public void setLocalParticipantUrl(String localParticipantUrl) {
        this.localParticipantUrl = localParticipantUrl;
    }

    public String getLocalParticipantContextPath() {
        return localParticipantContextPath;
    }

    /**
     * The context-path for the local participant.
     *
     * Is default /lra-participant
     */
    public void setLocalParticipantContextPath(String localParticipantContextPath) {
        this.localParticipantContextPath = localParticipantContextPath;
    }

    /**
     * The URL for the LRA coordinator service that orchestrates the transactions
     */
    public LraConfigurationProperties withCoordinatorUrl(String coordinatorUrl) {
        this.coordinatorUrl = coordinatorUrl;
        return this;
    }

    /**
     * To enable Saga LRA
     */
    public LraConfigurationProperties withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * The context-path for the LRA coordinator.
     */
    public LraConfigurationProperties withCoordinatorContextPath(String coordinatorContextPath) {
        this.coordinatorContextPath = coordinatorContextPath;
        return this;
    }

    /**
     * The URL for the local participant
     */
    public LraConfigurationProperties withLocalParticipantUrl(String localParticipantUrl) {
        this.localParticipantUrl = localParticipantUrl;
        return this;
    }

    /**
     * The context-path for the local participant.
     */
    public LraConfigurationProperties withLocalParticipantContextPath(String localParticipantContextPath) {
        this.localParticipantContextPath = localParticipantContextPath;
        return this;
    }

}
