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
package org.apache.camel.component.fhir.api;

import java.util.Map;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IFetchConformanceTyped;
import org.hl7.fhir.instance.model.api.IBaseConformance;

/**
 * API to Fetch the capability statement for the server
 */
public class FhirCapabilities {

    private final IGenericClient client;

    public FhirCapabilities(IGenericClient client) {
        this.client = client;
    }

    /**
     * Retrieve the conformance statement using the given model type
     * @param type the model type
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @param <T> extends {@link IBaseConformance}
     * @return the conformance statement
     */
    public <T extends IBaseConformance> T ofType(Class<T> type, Map<ExtraParameters, Object> extraParameters) {
        IFetchConformanceTyped<T> fetchConformanceTyped = client.capabilities().ofType(type);
        ExtraParameters.process(extraParameters, fetchConformanceTyped);
        return fetchConformanceTyped.execute();
    }

}
