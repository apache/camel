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
import ca.uhn.fhir.rest.gclient.IClientExecutable;
import org.hl7.fhir.instance.model.api.IBaseMetaType;
import org.hl7.fhir.instance.model.api.IIdType;

/**
 * API for the "meta" operations, which can be used to get, add and remove tags and other
 * Meta elements from a resource or across the server.
 */
public class FhirMeta {

    private final IGenericClient client;

    public FhirMeta(IGenericClient client) {
        this.client = client;
    }

    /**
     * Fetch the current metadata from the whole Server
     *
     * @param metaType The type of the meta datatype for the given FHIR model version (should be <code>MetaDt.class</code> or <code>MetaType.class</code>)
     * @param <T> extends {@link IBaseMetaType}
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @return the {@link IBaseMetaType}
     */
    public <T extends IBaseMetaType> T getFromServer(Class<T> metaType, Map<ExtraParameters, Object> extraParameters) {
        IClientExecutable<IClientExecutable<?, T>, T> clientExecutable = client.meta().get(metaType).fromServer();
        ExtraParameters.process(extraParameters, clientExecutable);
        return clientExecutable.execute();
    }

    /**
     * Fetch the current metadata from a specific resource
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @param metaType the {@link IBaseMetaType} class
     * @param id the id
     * @param <T> extends {@link IBaseMetaType}
     * @return the {@link IBaseMetaType}
     */
    public <T extends IBaseMetaType> T getFromResource(Class<T> metaType, IIdType id, Map<ExtraParameters, Object> extraParameters) {
        IClientExecutable<IClientExecutable<?, T>, T> clientExecutable = client.meta().get(metaType).fromResource(id);
        ExtraParameters.process(extraParameters, clientExecutable);
        return clientExecutable.execute();
    }

    /**
     * Fetch the current metadata from a specific type
     * @param <T> extends {@link IBaseMetaType}
     * @param metaType the {@link IBaseMetaType} class
     * @param resourceType the resource type e.g "Patient"
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @return the {@link IBaseMetaType}
     */
    public <T extends IBaseMetaType> T getFromType(Class<T> metaType, String resourceType, Map<ExtraParameters, Object> extraParameters) {
        IClientExecutable<IClientExecutable<?, T>, T> clientExecutable = client.meta().get(metaType).fromType(resourceType);
        ExtraParameters.process(extraParameters, clientExecutable);
        return clientExecutable.execute();
    }

    /**
     * Add the elements in the given metadata to the already existing set (do not remove any)
     * @param <T> extends {@link IBaseMetaType}
     * @param id the id
     * @param meta the {@link IBaseMetaType} class
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @return the {@link IBaseMetaType}
     */
    public <T extends IBaseMetaType> T add(T meta, IIdType id, Map<ExtraParameters, Object> extraParameters) {
        IClientExecutable<IClientExecutable<?, T>, T> clientExecutable = client.meta().add().onResource(id).meta(meta);
        ExtraParameters.process(extraParameters, clientExecutable);
        return clientExecutable.execute();
    }

    /**
     * Delete the elements in the given metadata from the given id
     * @param <T> extends {@link IBaseMetaType}
     * @param id the id
     * @param meta the {@link IBaseMetaType} class
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @return the {@link IBaseMetaType}
     */
    public <T extends IBaseMetaType> T delete(T meta, IIdType id, Map<ExtraParameters, Object> extraParameters) {
        IClientExecutable<IClientExecutable<?, T>, T> clientExecutable = client.meta().delete().onResource(id).meta(meta);
        ExtraParameters.process(extraParameters, clientExecutable);
        return clientExecutable.execute();
    }
}
