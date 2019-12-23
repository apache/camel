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

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.PreferReturnEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICreateTyped;
import org.hl7.fhir.instance.model.api.IBaseResource;

/**
 * API for the "create" operation, which creates a new resource instance on the server
 */
public class FhirCreate {

    private final IGenericClient client;

    public FhirCreate(IGenericClient client) {
        this.client = client;
    }

    /**
     * Creates a {@link IBaseResource} on the server
     *
     * @param resource The resource to create
     * @param url The search URL to use. The format of this URL should be of the form <code>[ResourceType]?[Parameters]</code>,
     *                     for example: <code>Patient?name=Smith&amp;identifier=13.2.4.11.4%7C847366</code>, may be null
     * @param preferReturn Add a <code>Prefer</code> header to the request, which requests that the server include
     *                  or suppress the resource body as a part of the result. If a resource is returned by the server
     *                  it will be parsed an accessible to the client via {@link MethodOutcome#getResource()}, may be null
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @return The {@link MethodOutcome}
     */
    public MethodOutcome resource(IBaseResource resource, String url, PreferReturnEnum preferReturn, Map<ExtraParameters, Object> extraParameters) {
        ICreateTyped createTyped = client.create().resource(resource);
        processOptionalParams(url, preferReturn, createTyped);
        ExtraParameters.process(extraParameters, createTyped);
        return createTyped.execute();
    }

    /**
     * Creates a {@link IBaseResource} on the server
     *
     * @param resourceAsString The resource to create
     * @param url The search URL to use. The format of this URL should be of the form <code>[ResourceType]?[Parameters]</code>,
     *                     for example: <code>Patient?name=Smith&amp;identifier=13.2.4.11.4%7C847366</code>, may be null
     * @param preferReturn Add a <code>Prefer</code> header to the request, which requests that the server include
     *                  or suppress the resource body as a part of the result. If a resource is returned by the server
     *                  it will be parsed an accessible to the client via {@link MethodOutcome#getResource()}, may be null
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @return The {@link MethodOutcome}
     */
    public MethodOutcome resource(String resourceAsString, String url, PreferReturnEnum preferReturn, Map<ExtraParameters, Object> extraParameters) {
        ICreateTyped createTyped = client.create().resource(resourceAsString);
        processOptionalParams(url, preferReturn, createTyped);
        ExtraParameters.process(extraParameters, createTyped);
        return createTyped.execute();
    }

    private void processOptionalParams(String theSearchUrl, PreferReturnEnum theReturn, ICreateTyped createTyped) {
        if (theSearchUrl != null) {
            createTyped.conditionalByUrl(theSearchUrl);
        }
        if (theReturn != null) {
            createTyped.prefer(theReturn);
        }
    }
}
