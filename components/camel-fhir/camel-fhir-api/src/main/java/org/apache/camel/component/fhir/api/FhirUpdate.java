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
import ca.uhn.fhir.rest.gclient.IUpdateExecutable;
import ca.uhn.fhir.rest.gclient.IUpdateTyped;
import org.apache.camel.util.ObjectHelper;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;

/**
 * API for the "update" operation, which performs a logical delete on a server resource
 */
public class FhirUpdate {

    private final IGenericClient client;

    public FhirUpdate(IGenericClient client) {
        this.client = client;
    }

    public MethodOutcome resource(IBaseResource resource, IIdType id, PreferReturnEnum preferReturn, Map<ExtraParameters, Object> extraParameters) {
        IUpdateTyped updateTyped = client.update().resource(resource);
        IUpdateExecutable updateExecutable = withOptionalId(id, updateTyped);
        ExtraParameters.process(extraParameters, updateExecutable);
        return processOptionalParam(preferReturn, updateExecutable);
    }

    public MethodOutcome resource(String resourceAsString, IIdType id, PreferReturnEnum preferReturn, Map<ExtraParameters, Object> extraParameters) {
        IUpdateTyped updateTyped = client.update().resource(resourceAsString);
        IUpdateExecutable updateExecutable = withOptionalId(id, updateTyped);
        ExtraParameters.process(extraParameters, updateExecutable);
        return processOptionalParam(preferReturn, updateExecutable);
    }

    public MethodOutcome resource(IBaseResource resource, String stringId, PreferReturnEnum preferReturn, Map<ExtraParameters, Object> extraParameters) {
        IUpdateTyped updateTyped = client.update().resource(resource);
        IUpdateExecutable updateExecutable = withOptionalId(stringId, updateTyped);
        ExtraParameters.process(extraParameters, updateExecutable);
        return processOptionalParam(preferReturn, updateExecutable);
    }

    public MethodOutcome resource(String resourceAsString, String stringId, PreferReturnEnum preferReturn, Map<ExtraParameters, Object> extraParameters) {
        IUpdateTyped updateTyped = client.update().resource(resourceAsString);
        IUpdateExecutable updateExecutable = withOptionalId(stringId, updateTyped);
        ExtraParameters.process(extraParameters, updateExecutable);
        return processOptionalParam(preferReturn, updateExecutable);
    }

    public MethodOutcome resourceBySearchUrl(IBaseResource resource, String url, PreferReturnEnum preferReturn, Map<ExtraParameters, Object> extraParameters) {
        IUpdateExecutable updateExecutable = client.update().resource(resource).conditionalByUrl(url);
        ExtraParameters.process(extraParameters, updateExecutable);
        return processOptionalParam(preferReturn, updateExecutable);
    }

    public MethodOutcome resourceBySearchUrl(String resourceAsString, String url, PreferReturnEnum preferReturn, Map<ExtraParameters, Object> extraParameters) {
        IUpdateExecutable updateExecutable = client.update().resource(resourceAsString).conditionalByUrl(url);
        ExtraParameters.process(extraParameters, updateExecutable);
        return processOptionalParam(preferReturn, updateExecutable);
    }

    private MethodOutcome processOptionalParam(PreferReturnEnum preferReturn, IUpdateExecutable updateExecutable) {
        if (preferReturn != null) {
            return updateExecutable.prefer(preferReturn).execute();
        }
        return updateExecutable.execute();
    }

    private IUpdateExecutable withOptionalId(IIdType id, IUpdateTyped updateTyped) {
        if (ObjectHelper.isNotEmpty(id)) {
            return updateTyped.withId(id);
        } else {
            return updateTyped;
        }
    }

    private IUpdateExecutable withOptionalId(String stringId, IUpdateTyped updateTyped) {
        if (ObjectHelper.isNotEmpty(stringId)) {
            return updateTyped.withId(stringId);
        } else {
            return updateTyped;
        }
    }
}
