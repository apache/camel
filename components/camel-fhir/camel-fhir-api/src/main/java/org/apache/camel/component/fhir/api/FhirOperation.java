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
import ca.uhn.fhir.rest.gclient.IOperationProcessMsg;
import ca.uhn.fhir.rest.gclient.IOperationProcessMsgMode;
import ca.uhn.fhir.rest.gclient.IOperationUnnamed;
import ca.uhn.fhir.rest.gclient.IOperationUntyped;
import ca.uhn.fhir.rest.gclient.IOperationUntypedWithInput;
import org.apache.camel.util.ObjectHelper;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;

/**
 * API for extended FHIR operations https://www.hl7.org/fhir/operations.html
 */
public class FhirOperation {

    private final IGenericClient client;

    public FhirOperation(IGenericClient client) {
        this.client = client;
    }

    /**
     * Perform the operation across all versions of all resources of the given type on the server
     *
     * @param resourceType The resource type to operate on
     * @param name Operation name
     * @param parameters The parameters to use as input. May also be <code>null</code> if the operation
     * does not require any input parameters.
     * @param outputParameterType The type to use for the output parameters (this should be set to
     * <code>Parameters.class</code> drawn from the version of the FHIR structures you are using), may be NULL
     * @param useHttpGet use HTTP GET verb
     * @param returnType If this operation returns a single resource body as its return type instead of a <code>Parameters</code>
     * resource, use this method to specify that resource type. This is useful for certain
     * operations (e.g. <code>Patient/NNN/$everything</code>) which return a bundle instead of
     * a Parameters resource, may be NULL
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @param <T> extends {@link IBaseParameters}
     * @return the {@link IBaseResource}
     */
    public <T extends IBaseParameters> IBaseResource onType(Class<IBaseResource> resourceType, String name,
    T parameters, Class<T> outputParameterType, boolean useHttpGet, Class<IBaseResource> returnType, Map<ExtraParameters, Object> extraParameters) {
        IOperationUnnamed operationUnnamed = client.operation().onType(resourceType);
        IOperationUntypedWithInput<? extends IBaseResource> operationUntypedWithInput = processNamedOperationParameters(
                name, parameters, outputParameterType, useHttpGet, returnType, extraParameters, operationUnnamed);
        return operationUntypedWithInput.execute();
    }

    /**
     * Perform the operation across all versions of all resources of all types on the server
     *
     * @param name Operation name
     * @param parameters The parameters to use as input. May also be <code>null</code> if the operation
     * does not require any input parameters.
     * @param outputParameterType The type to use for the output parameters (this should be set to
     * <code>Parameters.class</code> drawn from the version of the FHIR structures you are using), may be NULL
     * @param useHttpGet use HTTP GET verb
     * @param returnType If this operation returns a single resource body as its return type instead of a <code>Parameters</code>
     * resource, use this method to specify that resource type. This is useful for certain
     * operations (e.g. <code>Patient/NNN/$everything</code>) which return a bundle instead of
     * a Parameters resource, may be NULL
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @param <T> extends {@link IBaseParameters}
     * @return the {@link IBaseResource}
     */
    public <T extends IBaseParameters> IBaseResource onServer(
            String name, T parameters, Class<T> outputParameterType, boolean useHttpGet, Class<IBaseResource> returnType, Map<ExtraParameters, Object> extraParameters) {
        IOperationUnnamed operationUnnamed = client.operation().onServer();
        IOperationUntypedWithInput<? extends IBaseResource> operationUntypedWithInput = processNamedOperationParameters(
                name, parameters, outputParameterType, useHttpGet, returnType, extraParameters, operationUnnamed);
        return operationUntypedWithInput.execute();
    }

    /**
     * Perform the operation across all versions of a specific resource (by ID and type) on the server.
     * Note that <code>theId</code> must be populated with both a resource type and a resource ID at
     * a minimum.
     *
     * @param id Resource (version will be stripped)
     * @param name Operation name
     * @param parameters The parameters to use as input. May also be <code>null</code> if the operation
     * does not require any input parameters.
     * @param outputParameterType The type to use for the output parameters (this should be set to
     * <code>Parameters.class</code> drawn from the version of the FHIR structures you are using), may be NULL
     * @param useHttpGet use HTTP GET verb
     * @param returnType If this operation returns a single resource body as its return type instead of a <code>Parameters</code>
     * resource, use this method to specify that resource type. This is useful for certain
     * operations (e.g. <code>Patient/NNN/$everything</code>) which return a bundle instead of
     * a Parameters resource, may be NULL
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @param <T> extends {@link IBaseParameters}
     *
     * @throws IllegalArgumentException If <code>theId</code> does not contain at least a resource type and ID
     *
     * @return the {@link IBaseResource}
     */
    public <T extends IBaseParameters> IBaseResource onInstance(
            IIdType id, String name, T parameters, Class<T> outputParameterType, boolean useHttpGet, Class<IBaseResource> returnType, Map<ExtraParameters, Object> extraParameters) {
        IOperationUnnamed operationUnnamed = client.operation().onInstanceVersion(id);
        IOperationUntypedWithInput<? extends IBaseResource> operationUntypedWithInput = processNamedOperationParameters(
                name, parameters, outputParameterType, useHttpGet, returnType, extraParameters, operationUnnamed);
        return operationUntypedWithInput.execute();
    }


    /**
     * This operation operates on a specific version of a resource
     *
     * @param id Resource version
     * @param name Operation name
     * @param parameters The parameters to use as input. May also be <code>null</code> if the operation
     * does not require any input parameters.
     * @param outputParameterType The type to use for the output parameters (this should be set to
     * <code>Parameters.class</code> drawn from the version of the FHIR structures you are using), may be NULL
     * @param useHttpGet use HTTP GET verb
     * @param returnType If this operation returns a single resource body as its return type instead of a <code>Parameters</code>
     * resource, use this method to specify that resource type. This is useful for certain
     * operations (e.g. <code>Patient/NNN/$everything</code>) which return a bundle instead of
     * a Parameters resource, may be NULL
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @param <T> extends {@link IBaseParameters}
     * @return the {@link IBaseResource}
     */
    public <T extends IBaseParameters> IBaseResource onInstanceVersion(
            IIdType id, String name, T parameters, Class<T> outputParameterType, boolean useHttpGet, Class<IBaseResource> returnType, Map<ExtraParameters, Object> extraParameters) {
        IOperationUnnamed operationUnnamed = client.operation().onInstanceVersion(id);
        IOperationUntypedWithInput<? extends IBaseResource> operationUntypedWithInput = processNamedOperationParameters(
                name, parameters, outputParameterType, useHttpGet, returnType, extraParameters, operationUnnamed);
        return operationUntypedWithInput.execute();
    }

    /**
     * This operation is called <b><a href="https://www.hl7.org/fhir/messaging.html">$process-message</a></b> as defined by the FHIR
     * specification.<br><br>
     * @param respondToUri An optional query parameter indicating that responses from the receiving server should be sent to this URI, may be NULL
     * @param msgBundle Set the Message Bundle to POST to the messaging server
     * @param asynchronous Whether to process the message asynchronously or synchronously, defaults to synchronous.
     * @param responseClass the response class
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @param <T> extends {@link IBaseBundle}
     * @return the {@link IBaseBundle}
     */
    public <T extends IBaseBundle> T processMessage(String respondToUri, IBaseBundle msgBundle, boolean asynchronous, Class<T> responseClass, Map<ExtraParameters, Object> extraParameters) {
        IOperationProcessMsg operationProcessMsg = client.operation().processMessage();

        if (ObjectHelper.isNotEmpty(respondToUri)) {
            operationProcessMsg.setResponseUrlParam(respondToUri);
        }

        IOperationProcessMsgMode<T> operationProcessMsgMode = operationProcessMsg.setMessageBundle(msgBundle);

        if (asynchronous) {
            operationProcessMsgMode.asynchronous(responseClass);
        } else {
            operationProcessMsgMode.synchronous(responseClass);
        }

        ExtraParameters.process(extraParameters, operationProcessMsgMode);
        return operationProcessMsgMode.execute();
    }

    private <T extends IBaseParameters> IOperationUntypedWithInput<? extends IBaseResource> processNamedOperationParameters(String name, T parameters, Class<T> outputParameterType,
    boolean useHttpGet, Class<? extends IBaseResource> returnType, Map<ExtraParameters, Object> extraParameters, IOperationUnnamed operationUnnamed) {
        IOperationUntyped named = operationUnnamed.named(name);
        IOperationUntypedWithInput<? extends IBaseResource> operationUntypedWithInput;
        if (outputParameterType != null) {
            operationUntypedWithInput = named.withNoParameters(outputParameterType);
        } else {
            operationUntypedWithInput = named.withParameters(parameters);
        }
        if (useHttpGet) {
            operationUntypedWithInput.useHttpGet();
        }
        if (returnType != null) {
            operationUntypedWithInput.returnResourceType(returnType);
        }
        ExtraParameters.process(extraParameters, operationUntypedWithInput);
        return operationUntypedWithInput;
    }
}
