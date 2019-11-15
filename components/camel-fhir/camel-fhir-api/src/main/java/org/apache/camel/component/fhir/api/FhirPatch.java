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
import ca.uhn.fhir.rest.gclient.IPatchExecutable;
import org.hl7.fhir.instance.model.api.IIdType;

/**
 * API for the "patch" operation, which performs a logical patch on a server resource
 */
public class FhirPatch {

    private final IGenericClient client;

    public FhirPatch(IGenericClient client) {
        this.client = client;
    }

    /**
     * Specifies that the update should be performed as a conditional create
     * against a given search URL.
     *
     * @param url The search URL to use. The format of this URL should be of the form <code>[ResourceType]?[Parameters]</code>,
     *            for example: <code>Patient?name=Smith&amp;identifier=13.2.4.11.4%7C847366</code>
     * @param patchBody The body of the patch document serialized in either XML or JSON which conforms to
     *                  http://jsonpatch.com/ or http://tools.ietf.org/html/rfc5261
     * @param preferReturn Add a <code>Prefer</code> header to the request, which requests that the server include
     *                     or suppress the resource body as a part of the result. If a resource is returned by the server
     *                     it will be parsed an accessible to the client via {@link MethodOutcome#getResource()}
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @return the {@link MethodOutcome}
     */
    public MethodOutcome patchByUrl(String patchBody, String url, PreferReturnEnum preferReturn, Map<ExtraParameters, Object> extraParameters) {
        IPatchExecutable patchExecutable = client.patch().withBody(patchBody).conditionalByUrl(url);
        if (preferReturn != null) {
            patchExecutable.prefer(preferReturn);
        }
        ExtraParameters.process(extraParameters, patchExecutable);
        return patchExecutable.execute();
    }

    /**
     * Applies the patch to the given resource ID
     *
     * @param patchBody The body of the patch document serialized in either XML or JSON which conforms to
     *                  http://jsonpatch.com/ or http://tools.ietf.org/html/rfc5261
     * @param id The resource ID to patch
     * @param preferReturn Add a <code>Prefer</code> header to the request, which requests that the server include
     *                     or suppress the resource body as a part of the result. If a resource is returned by the server
     *                     it will be parsed an accessible to the client via {@link MethodOutcome#getResource()}
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @return the {@link MethodOutcome}
     */
    public MethodOutcome patchById(String patchBody, IIdType id, PreferReturnEnum preferReturn, Map<ExtraParameters, Object> extraParameters) {
        IPatchExecutable patchExecutable = client.patch().withBody(patchBody).withId(id);
        if (preferReturn != null) {
            patchExecutable.prefer(preferReturn);
        }
        ExtraParameters.process(extraParameters, patchExecutable);
        return patchExecutable.execute();
    }

    /**
     * Applies the patch to the given resource ID
     *
     * @param patchBody The body of the patch document serialized in either XML or JSON which conforms to
     *                  http://jsonpatch.com/ or http://tools.ietf.org/html/rfc5261
     * @param stringId The resource ID to patch
     * @param preferReturn Add a <code>Prefer</code> header to the request, which requests that the server include
     *                     or suppress the resource body as a part of the result. If a resource is returned by the server
     *                     it will be parsed an accessible to the client via {@link MethodOutcome#getResource()}
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @return the {@link MethodOutcome}
     */
    public MethodOutcome patchById(String patchBody, String stringId, PreferReturnEnum preferReturn, Map<ExtraParameters, Object> extraParameters) {
        IPatchExecutable patchExecutable = client.patch().withBody(patchBody).withId(stringId);
        if (preferReturn != null) {
            patchExecutable.prefer(preferReturn);
        }
        ExtraParameters.process(extraParameters, patchExecutable);
        return patchExecutable.execute();
    }

}
