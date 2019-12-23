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
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IValidateUntyped;
import org.hl7.fhir.instance.model.api.IBaseResource;

/**
 * API for validating resources
 */
public class FhirValidate {

    private final IGenericClient client;

    public FhirValidate(IGenericClient client) {
        this.client = client;
    }

    public MethodOutcome resource(IBaseResource resource, Map<ExtraParameters, Object> extraParameters) {
        IValidateUntyped validateUntyped = client.validate().resource(resource);
        ExtraParameters.process(extraParameters, validateUntyped);
        return validateUntyped.execute();
    }

    public MethodOutcome resource(String resourceAsString, Map<ExtraParameters, Object> extraParameters) {
        IValidateUntyped validateUntyped = client.validate().resource(resourceAsString);
        ExtraParameters.process(extraParameters, validateUntyped);
        return validateUntyped.execute();
    }
}
