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
import ca.uhn.fhir.rest.gclient.IReadExecutable;
import ca.uhn.fhir.rest.gclient.IReadIfNoneMatch;
import ca.uhn.fhir.rest.gclient.IReadTyped;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * API method for "read" operations
 */
public class FhirRead {

    private static final Logger LOG = LoggerFactory.getLogger(FhirRead.class);
    private final IGenericClient client;


    public FhirRead(IGenericClient client) {
        this.client = client;
    }


    public IBaseResource resourceById(Class<IBaseResource> resource, IIdType id,  String ifVersionMatches,
                                      Boolean returnNull, IBaseResource returnResource, Boolean throwError, Map<ExtraParameters, Object> extraParameters) {
        IReadExecutable<IBaseResource> readExecutable = client.read().resource(resource).withId(id);
        ExtraParameters.process(extraParameters, readExecutable);
        return processOptionalParams(ifVersionMatches, returnNull, returnResource, throwError, readExecutable);
    }

    public IBaseResource resourceById(String resourceClass, IIdType id, String ifVersionMatches, Boolean returnNull,
                                      IBaseResource returnResource, Boolean throwError, Map<ExtraParameters, Object> extraParameters) {
        IReadExecutable<IBaseResource> readExecutable = client.read().resource(resourceClass).withId(id);
        ExtraParameters.process(extraParameters, readExecutable);
        return processOptionalParams(ifVersionMatches, returnNull, returnResource, throwError, readExecutable);
    }

    public IBaseResource  resourceById(Class<IBaseResource> resource, String stringId, String version, String ifVersionMatches,
                                       Boolean returnNull, IBaseResource returnResource, Boolean throwError, Map<ExtraParameters, Object> extraParameters) {
        IReadTyped<IBaseResource> readTyped = client.read().resource(resource);
        IReadExecutable<IBaseResource> readExecutable = readWithOptionalVersion(stringId, readTyped, version);
        ExtraParameters.process(extraParameters, readExecutable);
        return processOptionalParams(ifVersionMatches, returnNull, returnResource, throwError, readExecutable);
    }

    public IBaseResource resourceById(String resourceClass, String stringId, String ifVersionMatches, String version,
                                      Boolean returnNull, IBaseResource returnResource, Boolean throwError, Map<ExtraParameters, Object> extraParameters) {
        IReadTyped<IBaseResource> resource = client.read().resource(resourceClass);
        IReadExecutable<IBaseResource> readExecutable = readWithOptionalVersion(stringId, resource, version);
        ExtraParameters.process(extraParameters, readExecutable);
        return processOptionalParams(ifVersionMatches, returnNull, returnResource, throwError, readExecutable);
    }

    public IBaseResource resourceById(Class<IBaseResource> resource, Long longId, String ifVersionMatches,
                                      Boolean returnNull, IBaseResource returnResource, Boolean throwError, Map<ExtraParameters, Object> extraParameters) {
        IReadExecutable<IBaseResource> readExecutable = client.read().resource(resource).withId(longId);
        ExtraParameters.process(extraParameters, readExecutable);
        return processOptionalParams(ifVersionMatches, returnNull, returnResource, throwError, readExecutable);
    }

    public IBaseResource resourceById(String resourceClass, Long longId, String ifVersionMatches, Boolean returnNull,
                                      IBaseResource returnResource, Boolean throwError, Map<ExtraParameters, Object> extraParameters) {
        IReadExecutable<IBaseResource> readExecutable = client.read().resource(resourceClass).withId(longId);
        ExtraParameters.process(extraParameters, readExecutable);
        return processOptionalParams(ifVersionMatches, returnNull, returnResource, throwError, readExecutable);
    }

    public IBaseResource resourceByUrl(Class<IBaseResource> resource, IIdType iUrl, String ifVersionMatches,
                                       Boolean returnNull, IBaseResource returnResource, Boolean throwError, Map<ExtraParameters, Object> extraParameters) {
        IReadExecutable<IBaseResource> readExecutable = client.read().resource(resource).withUrl(iUrl);
        ExtraParameters.process(extraParameters, readExecutable);
        return processOptionalParams(ifVersionMatches, returnNull, returnResource, throwError, readExecutable);
    }

    public IBaseResource resourceByUrl(String resourceClass, IIdType iUrl, String ifVersionMatches, Boolean returnNull,
                                       IBaseResource returnResource, Boolean throwError, Map<ExtraParameters, Object> extraParameters) {
        IReadExecutable<IBaseResource> readExecutable = client.read().resource(resourceClass).withUrl(iUrl);
        ExtraParameters.process(extraParameters, readExecutable);
        return processOptionalParams(ifVersionMatches, returnNull, returnResource, throwError, readExecutable);
    }

    public IBaseResource resourceByUrl(Class<IBaseResource> resource, String url, String ifVersionMatches,
                                       Boolean returnNull, IBaseResource returnResource, Boolean throwError, Map<ExtraParameters, Object> extraParameters) {
        IReadExecutable<IBaseResource> readExecutable = client.read().resource(resource).withUrl(url);
        ExtraParameters.process(extraParameters, readExecutable);
        return processOptionalParams(ifVersionMatches, returnNull, returnResource, throwError, readExecutable);
    }

    public IBaseResource resourceByUrl(String resourceClass, String url, String ifVersionMatches, Boolean returnNull,
                                       IBaseResource returnResource, Boolean throwError, Map<ExtraParameters, Object> extraParameters) {
        IReadExecutable<IBaseResource> readExecutable = client.read().resource(resourceClass).withUrl(url);
        ExtraParameters.process(extraParameters, readExecutable);
        return processOptionalParams(ifVersionMatches, returnNull, returnResource, throwError, readExecutable);
    }

    private IBaseResource processOptionalParams(String ifVersionMatches, Boolean returnNull, IBaseResource returnResource,
                                                Boolean throwError, IReadExecutable<IBaseResource> readExecutable) {
        if (ifVersionMatches != null) {
            IReadIfNoneMatch<IBaseResource> tiReadIfNoneMatch = readExecutable.ifVersionMatches(ifVersionMatches);
            if (returnNull != null  && returnNull) {
                return tiReadIfNoneMatch.returnNull().execute();
            } else if (returnResource != null) {
                return tiReadIfNoneMatch.returnResource(returnResource).execute();
            } else if (throwError != null) {
                return tiReadIfNoneMatch.throwNotModifiedException().execute();
            }
            LOG.warn("No operation was specified with the If-None-Match header, ignoring");
        }
        return readExecutable.execute();
    }

    private IReadExecutable<IBaseResource> readWithOptionalVersion(String stringId, IReadTyped<IBaseResource> resource, String version) {
        if (version != null) {
            return resource.withIdAndVersion(stringId, version);
        }
        return resource.withId(stringId);
    }
}
