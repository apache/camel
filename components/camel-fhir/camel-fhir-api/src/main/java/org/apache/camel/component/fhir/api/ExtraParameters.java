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

import ca.uhn.fhir.rest.api.CacheControlDirective;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.SummaryEnum;
import ca.uhn.fhir.rest.gclient.IClientExecutable;
import org.hl7.fhir.instance.model.api.IBaseResource;

/**
 * Encapsulates a list of extra parameters that are valid for *ALL* Camel FHIR APIs.
 */
public enum ExtraParameters {

    /**
     * Will encode the request to JSON
     */
    ENCODE_JSON("encodeJson"),

    /**
     * Will encode the request to XML
     */
    ENCODE_XML("encodeXml"),

    /**
     * Sets the <code>Cache-Control</code> header value, which advises the server (or any cache in front of it)
     * how to behave in terms of cached requests"
     */
    CACHE_CONTROL_DIRECTIVE("cacheControlDirective"),

    /**
     * Request that the server return subsetted resources, containing only the elements specified in the given parameters.
     * For example: <code>subsetElements("name", "identifier")</code> requests that the server only return
     * the "name" and "identifier" fields in the returned resource, and omit any others.
     */
    SUBSET_ELEMENTS("subsetElements"),


    ENCODING_ENUM("encodingEnum"),

    /**
     * Explicitly specify a custom structure type to attempt to use when parsing the response. This
     * is useful for invocations where the response is a Bundle/Parameters containing nested resources,
     * and you want to use specific custom structures for those nested resources.
     * <p>
     * See <a href="https://jamesagnew.github.io/hapi-fhir/doc_extensions.html">Profiles and Extensions</a> for more information on using custom structures
     * </p>
     */
    PREFER_RESPONSE_TYPE("preferredResponseType"),

    /**
     * Explicitly specify a custom structure type to attempt to use when parsing the response. This
     * is useful for invocations where the response is a Bundle/Parameters containing nested resources,
     * and you want to use specific custom structures for those nested resources.
     * <p>
     * See <a href="https://jamesagnew.github.io/hapi-fhir/doc_extensions.html">Profiles and Extensions</a> for more information on using custom structures
     * </p>
     */
    PREFER_RESPONSE_TYPES("preferredResponseTypes"),

    /**
     * Pretty print the request
     */
    PRETTY_PRINT("prettyPrint"),

    /**
     * Request that the server modify the response using the <code>_summary</code> param
     */
    SUMMARY_ENUM("summaryEnum");

    private final String param;
    private final String headerName;

    ExtraParameters(String param) {
        this.param = param;
        this.headerName = "CamelFhir." + param;
    }

    public String getParam() {
        return param;
    }

    public String getHeaderName() {
        return headerName;
    }

    static <T extends IClientExecutable<?, ?>> void process(Map<ExtraParameters, Object> extraParameters, T clientExecutable) {
        if (extraParameters == null) {
            return;
        }
        for (Map.Entry<ExtraParameters, Object> entry : extraParameters.entrySet()) {
            switch (entry.getKey()) {
                case ENCODE_JSON:
                    Boolean encode = (Boolean) extraParameters.get(ENCODE_JSON);
                    if (encode) {
                        clientExecutable.encodedJson();
                    }
                    break;
                case ENCODE_XML:
                    Boolean encodeXml = (Boolean) extraParameters.get(ENCODE_XML);
                    if (encodeXml) {
                        clientExecutable.encodedXml();
                    }
                    break;
                case CACHE_CONTROL_DIRECTIVE:
                    CacheControlDirective cacheControlDirective = (CacheControlDirective) extraParameters.get(CACHE_CONTROL_DIRECTIVE);
                    clientExecutable.cacheControl(cacheControlDirective);
                    break;
                case SUBSET_ELEMENTS:
                    String[] subsetElements = (String[]) extraParameters.get(SUBSET_ELEMENTS);
                    clientExecutable.elementsSubset(subsetElements);
                    break;
                case ENCODING_ENUM:
                    EncodingEnum encodingEnum = (EncodingEnum) extraParameters.get(ENCODING_ENUM);
                    clientExecutable.encoded(encodingEnum);
                    break;
                case PREFER_RESPONSE_TYPE:
                    Class<? extends IBaseResource> type = (Class<? extends IBaseResource>) extraParameters.get(PREFER_RESPONSE_TYPE);
                    clientExecutable.preferResponseType(type);
                    break;
                case PREFER_RESPONSE_TYPES:
                    List<Class<? extends IBaseResource>> types = (List<Class<? extends IBaseResource>>) extraParameters.get(PREFER_RESPONSE_TYPES);
                    clientExecutable.preferResponseTypes(types);
                    break;
                case PRETTY_PRINT:
                    Boolean prettyPrint = (Boolean) extraParameters.get(PRETTY_PRINT);
                    if (prettyPrint) {
                        clientExecutable.prettyPrint();
                    }
                    break;
                case SUMMARY_ENUM:
                    SummaryEnum summary = (SummaryEnum) extraParameters.get(SUMMARY_ENUM);
                    clientExecutable.summaryMode(summary);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported FHIR extra parameter parameter: " + entry.getKey());
            }
        }
    }
}
