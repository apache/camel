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

import java.util.Date;
import java.util.Map;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IHistoryTyped;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.instance.model.api.IPrimitiveType;

/**
 * API for the "history" method
 */
public class FhirHistory {

    private final IGenericClient client;

    public FhirHistory(IGenericClient client) {
        this.client = client;
    }

    /**
     * Perform the operation across all versions of all resources of all types on the server
     *
     * @param returnType Request that the method return a Bundle resource (such as <code>ca.uhn.fhir.model.dstu2.resource.Bundle</code>).
     *                Use this method if you are accessing a DSTU2+ server.
     * @param count Request that the server return only up to <code>theCount</code> number of resources, may be NULL
     * @param cutoff Request that the server return only resource versions that were created at or after the given time (inclusive), may be NULL
     * @param iCutoff Request that the server return only resource versions that were created at or after the given time (inclusive), may be NULL
     * @param <T> extends {@link IBaseBundle}
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @return the {@link IBaseBundle}
     */
    public <T extends IBaseBundle> T onServer(Class<T> returnType, Integer count, Date cutoff, IPrimitiveType<Date> iCutoff, Map<ExtraParameters, Object> extraParameters) {
        IHistoryTyped<T> tiHistoryTyped = client.history().onServer().andReturnBundle(returnType);
        processOptionalParams(count, cutoff, iCutoff, tiHistoryTyped);
        ExtraParameters.process(extraParameters, tiHistoryTyped);
        return tiHistoryTyped.execute();
    }

    /**
     * Perform the operation across all versions of all resources of the given type on the server
     *
     * @param resourceType The resource type to search for
     * @param returnType Request that the method return a Bundle resource (such as <code>ca.uhn.fhir.model.dstu2.resource.Bundle</code>).
     *                Use this method if you are accessing a DSTU2+ server.
     * @param count Request that the server return only up to <code>theCount</code> number of resources, may be NULL
     * @param cutoff Request that the server return only resource versions that were created at or after the given time (inclusive), may be NULL
     * @param iCutoff Request that the server return only resource versions that were created at or after the given time (inclusive), may be NULL
     * @param <T> extends {@link IBaseBundle}
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @return the {@link IBaseBundle}
     */
    public <T extends IBaseBundle> T onType(Class<IBaseResource> resourceType, Class<T> returnType, Integer count, Date cutoff,
                                            IPrimitiveType<Date> iCutoff, Map<ExtraParameters, Object> extraParameters) {
        IHistoryTyped<T> tiHistoryTyped = client.history().onType(resourceType).andReturnBundle(returnType);
        processOptionalParams(count, cutoff, iCutoff, tiHistoryTyped);
        ExtraParameters.process(extraParameters, tiHistoryTyped);
        return tiHistoryTyped.execute();
    }

    /**
     * Perform the operation across all versions of a specific resource (by ID and type) on the server.
     * Note that <code>theId</code> must be populated with both a resource type and a resource ID at
     * a minimum.
     * @param id the {@link IIdType} which must be populated with both a resource type and a resource ID at
     * @param returnType Request that the method return a Bundle resource (such as <code>ca.uhn.fhir.model.dstu2.resource.Bundle</code>).
     *                Use this method if you are accessing a DSTU2+ server.
     * @param count Request that the server return only up to <code>theCount</code> number of resources, may be NULL
     * @param cutoff Request that the server return only resource versions that were created at or after the given time (inclusive), may be NULL
     * @param iCutoff Request that the server return only resource versions that were created at or after the given time (inclusive), may be NULL
     * @param <T> extends {@link IBaseBundle}
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @throws IllegalArgumentException If <code>id</code> does not contain at least a resource type and ID
     * @return the {@link IBaseBundle}
     */
    public <T extends IBaseBundle> T onInstance(IIdType id, Class<T> returnType, Integer count, Date cutoff, IPrimitiveType<Date> iCutoff, Map<ExtraParameters, Object> extraParameters) {
        IHistoryTyped<T> tiHistoryTyped = client.history().onInstance(id).andReturnBundle(returnType);
        processOptionalParams(count, cutoff, iCutoff, tiHistoryTyped);
        ExtraParameters.process(extraParameters, tiHistoryTyped);
        return tiHistoryTyped.execute();
    }

    private <T extends IBaseBundle> void processOptionalParams(Integer count, Date theCutoff, IPrimitiveType<Date> cutoff, IHistoryTyped<T> tiHistoryTyped) {
        if (count != null) {
            tiHistoryTyped.count(count);
        }
        if (theCutoff != null) {
            tiHistoryTyped.since(theCutoff);
        }
        if (cutoff != null) {
            tiHistoryTyped.since(cutoff);
        }
    }

}
