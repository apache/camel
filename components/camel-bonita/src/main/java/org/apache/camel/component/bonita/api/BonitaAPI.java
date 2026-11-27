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
package org.apache.camel.component.bonita.api;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.bonita.api.model.CaseCreationResponse;
import org.apache.camel.component.bonita.api.model.ProcessDefinitionResponse;
import org.apache.camel.component.bonita.api.util.BonitaAPIConfig;
import org.apache.camel.component.bonita.api.util.BonitaAPIUtil;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static jakarta.ws.rs.client.Entity.entity;

public class BonitaAPI {

    private static final Logger LOG = LoggerFactory.getLogger(BonitaAPI.class);

    private BonitaAPIConfig bonitaApiConfig;
    private WebTarget webTarget;
    private final BonitaAPIUtil bonitaApiUtil;
    // the process names already warned about having several versions, so that the WARN is not logged for every exchange
    private final Set<String> warnedSeveralVersions = ConcurrentHashMap.newKeySet();

    /**
     * @deprecated the file inputs are uploaded with a JAX-RS client of their own, which is never closed. Use
     *             {@link #BonitaAPI(BonitaAPIConfig, WebTarget, BonitaAPIUtil)} with a helper from
     *             {@link BonitaAPIUtil#forClient}, as {@code BonitaAPIBuilder} does.
     */
    @Deprecated(since = "4.24.0")
    protected BonitaAPI(BonitaAPIConfig bonitaApiConfig, WebTarget webTarget) {
        this(bonitaApiConfig, webTarget, BonitaAPIUtil.getInstance(bonitaApiConfig));
    }

    /**
     * @param bonitaApiUtil the helper that uploads the file inputs of {@link #startCase}; it has to use the same Bonita
     *                      server and account as the given target
     */
    protected BonitaAPI(BonitaAPIConfig bonitaApiConfig, WebTarget webTarget, BonitaAPIUtil bonitaApiUtil) {
        this.bonitaApiConfig = bonitaApiConfig;
        this.webTarget = webTarget;
        this.bonitaApiUtil = bonitaApiUtil;
    }

    private WebTarget getBaseResource() {
        return webTarget;
    }

    /**
     * The process with exactly this name, in any version: see {@link #getProcessDefinition(String, String)}.
     */
    public ProcessDefinitionResponse getProcessDefinition(String processName) {
        return getProcessDefinition(processName, null);
    }

    /**
     * The process with exactly this name and, when processVersion is not empty, this version. The name and the version
     * are filters ({@code f=name=...}, {@code f=version=...}) of the Bonita REST API, not a search term, so another
     * process whose name only contains the given name is not used. When several versions match, the first one that
     * Bonita returns is used, and a WARN is logged the first time for each process name (later at DEBUG).
     */
    public ProcessDefinitionResponse getProcessDefinition(String processName, String processVersion) {
        if (ObjectHelper.isEmpty(processName)) {
            throw new IllegalArgumentException("processName is empty.");
        }
        WebTarget resource = getBaseResource().path("process").queryParam("f", "name=" + processName);
        if (ObjectHelper.isNotEmpty(processVersion)) {
            resource = resource.queryParam("f", "version=" + processVersion);
        }
        List<ProcessDefinitionResponse> listProcess = resource.request().accept(MediaType.APPLICATION_JSON)
                .get(new GenericType<List<ProcessDefinitionResponse>>() {
                });
        if (listProcess.isEmpty()) {
            throw new RuntimeCamelException(
                    "The process with name " + processName
                                            + (ObjectHelper.isNotEmpty(processVersion) ? " and version " + processVersion : "")
                                            + " has not been retrieved");
        }
        ProcessDefinitionResponse first = listProcess.get(0);
        if (listProcess.size() > 1) {
            if (warnedSeveralVersions.add(processName)) {
                LOG.warn("{} versions of the process {} are deployed, using version {}. Set processVersion to choose one."
                         + " This is logged once for this process, later lookups log it at DEBUG.",
                        listProcess.size(), processName, first.getVersion());
            } else {
                LOG.debug("{} versions of the process {} are deployed, using version {}", listProcess.size(), processName,
                        first.getVersion());
            }
        }
        return first;
    }

    public CaseCreationResponse startCase(
            ProcessDefinitionResponse processDefinition,
            Map<String, Serializable> rawInputs)
            throws Exception {
        if (processDefinition == null) {
            throw new IllegalArgumentException("ProcessDefinition is null");
        }
        if (rawInputs == null) {
            throw new IllegalArgumentException("The contract input is null");
        }
        Map<String, Serializable> inputs = bonitaApiUtil.prepareInputs(processDefinition, rawInputs);
        WebTarget resource = getBaseResource().path("process/{processId}/instantiation")
                .resolveTemplate("processId", processDefinition.getId());
        return resource.request().accept(MediaType.APPLICATION_JSON)
                .post(entity(inputs, MediaType.APPLICATION_JSON), CaseCreationResponse.class);
    }

    public BonitaAPIConfig getBonitaApiConfig() {
        return bonitaApiConfig;
    }

    public void setBonitaApiConfig(BonitaAPIConfig bonitaApiConfig) {
        this.bonitaApiConfig = bonitaApiConfig;
    }

}
