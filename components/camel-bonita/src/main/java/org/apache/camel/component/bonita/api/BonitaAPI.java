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

package org.apache.camel.component.bonita.api;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;

import static javax.ws.rs.client.Entity.entity;

import org.apache.camel.component.bonita.api.model.CaseCreationResponse;
import org.apache.camel.component.bonita.api.model.ProcessDefinitionResponse;
import org.apache.camel.component.bonita.api.util.BonitaAPIConfig;
import org.apache.camel.component.bonita.api.util.BonitaAPIUtil;
import org.apache.camel.util.ObjectHelper;



public class BonitaAPI {

    private BonitaAPIConfig bonitaApiConfig;
    private WebTarget webTarget;

    protected BonitaAPI(BonitaAPIConfig bonitaApiConfig, WebTarget webTarget) {
        super();
        this.bonitaApiConfig = bonitaApiConfig;
        this.webTarget = webTarget;

    }

    private WebTarget getBaseResource() {
        return webTarget;
    }

    public ProcessDefinitionResponse getProcessDefinition(String processName) {
        if (ObjectHelper.isEmpty(processName)) {
            throw new IllegalArgumentException("processName is empty.");
        }
        WebTarget resource = getBaseResource().path("process").queryParam("s", processName);
        List<ProcessDefinitionResponse> listProcess =
                resource.request().accept(MediaType.APPLICATION_JSON)
                        .get(new GenericType<List<ProcessDefinitionResponse>>() {
                        });
        if (listProcess.size() > 0) {
            return listProcess.get(0);
        } else {
            throw new RuntimeException(
                    "The process with name " + processName + " has not been retrieved");
        }
    }

    public CaseCreationResponse startCase(ProcessDefinitionResponse processDefinition,
            Map<String, Serializable> rawInputs) throws Exception {
        if (processDefinition == null) {
            throw new IllegalArgumentException("ProcessDefinition is null");
        }
        if (rawInputs == null) {
            throw new IllegalArgumentException("The contract input is null");
        }
        Map<String, Serializable> inputs = BonitaAPIUtil.getInstance(bonitaApiConfig)
                .prepareInputs(processDefinition, rawInputs);
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
