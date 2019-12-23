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
import ca.uhn.fhir.rest.gclient.IQuery;
import org.hl7.fhir.instance.model.api.IBaseBundle;

/**
 * API to search for resources matching a given set of criteria. Searching is a very powerful
 * feature in FHIR with many features for specifying exactly what should be searched for
 * and how it should be returned. See the <a href="http://www.hl7.org/fhir/search.html">specification on search</a>
 * for more information.
 */
public class FhirSearch {

    private final IGenericClient client;

    public FhirSearch(IGenericClient client) {
        this.client = client;
    }


    /**
     * Perform a search directly by URL.
     *
     * @param url The URL to search for. Note that this URL may be complete (e.g. "http://example.com/base/Patient?name=foo") in which case the client's base URL will be ignored. Or it can be relative
     *            (e.g. "Patient?name=foo") in which case the client's base URL will be used.
     * @param extraParameters see {@link ExtraParameters} for a full list of parameters that can be passed, may be NULL
     * @return the {@link IBaseBundle}
     */
    public IBaseBundle searchByUrl(String url, Map<ExtraParameters, Object> extraParameters) {
        IQuery<IBaseBundle> query = client.search().byUrl(url);
        ExtraParameters.process(extraParameters, query);
        return query.execute();
    }

}
