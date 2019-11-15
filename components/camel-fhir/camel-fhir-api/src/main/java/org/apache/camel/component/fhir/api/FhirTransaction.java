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

import java.util.List;
import java.util.Map;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ITransactionTyped;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;

/**
 * API for sending a transaction (collection of resources) to the server to be executed as a single unit.
 */
public class FhirTransaction {

    private final IGenericClient client;

    public FhirTransaction(IGenericClient client) {
        this.client = client;
    }

    /**
     * Use a list of resources as the transaction input
     * @param resources resources to use in the transaction
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @return the {@link IBaseResource}s
     */
    public List<IBaseResource> withResources(List<IBaseResource> resources, Map<ExtraParameters, Object> extraParameters) {
        ITransactionTyped<List<IBaseResource>> transactionTyped = client.transaction().withResources(resources);
        ExtraParameters.process(extraParameters, transactionTyped);
        return transactionTyped.execute();
    }

    /**
     * Use the given Bundle resource as the transaction input
     * @param bundle bundle to use in the transaction
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @return the {@link IBaseBundle}
     */
    public IBaseBundle withBundle(IBaseBundle bundle, Map<ExtraParameters, Object> extraParameters) {
        ITransactionTyped<IBaseBundle> transactionTyped = client.transaction().withBundle(bundle);
        ExtraParameters.process(extraParameters, transactionTyped);
        return transactionTyped.execute();
    }

    /**
     * Use the given raw text (should be a Bundle resource) as the transaction input
     * @param stringBundle bundle to use in the transaction
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @return the {@link IBaseBundle} as string
     */
    public String withBundle(String stringBundle, Map<ExtraParameters, Object> extraParameters) {
        ITransactionTyped<String> transactionTyped = client.transaction().withBundle(stringBundle);
        ExtraParameters.process(extraParameters, transactionTyped);
        return transactionTyped.execute();
    }
}
