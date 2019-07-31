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
package org.apache.camel.component.fhir.internal;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.context.PerformanceOptionsEnum;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.SummaryEnum;
import ca.uhn.fhir.rest.client.apache.GZipContentInterceptor;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import ca.uhn.fhir.rest.client.interceptor.CookieInterceptor;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.fhir.FhirConfiguration;
import org.apache.camel.util.ObjectHelper;

/**
 * Utility class for creating FHIR {@link ca.uhn.fhir.rest.client.api.IGenericClient}
 */
public final class FhirHelper {

    private FhirHelper() {
        // hide utility class constructor
    }

    public static IGenericClient createClient(FhirConfiguration endpointConfiguration, CamelContext camelContext) {
        if (endpointConfiguration.getClient() != null) {
            return endpointConfiguration.getClient();
        }
        FhirContext fhirContext = getFhirContext(endpointConfiguration);
        if (endpointConfiguration.isDeferModelScanning()) {
            fhirContext.setPerformanceOptions(PerformanceOptionsEnum.DEFERRED_MODEL_SCANNING);
        }
        if (endpointConfiguration.getClientFactory() != null) {
            fhirContext.setRestfulClientFactory(endpointConfiguration.getClientFactory());
        }

        IRestfulClientFactory restfulClientFactory = fhirContext.getRestfulClientFactory();
        configureClientFactory(endpointConfiguration, restfulClientFactory, camelContext);
        IGenericClient genericClient = fhirContext.newRestfulGenericClient(endpointConfiguration.getServerUrl());
        genericClient.setPrettyPrint(endpointConfiguration.isPrettyPrint());
        EncodingEnum encoding = endpointConfiguration.getEncoding();
        SummaryEnum summary = endpointConfiguration.getSummary();

        if (encoding != null) {
            genericClient.setEncoding(encoding);
        }
        if (summary != null) {
            genericClient.setSummary(summary);
        }
        if (endpointConfiguration.isForceConformanceCheck()) {
            genericClient.forceConformanceCheck();
        }

        registerClientInterceptors(genericClient, endpointConfiguration);
        return genericClient;
    }

    private static void configureClientFactory(FhirConfiguration endpointConfiguration, IRestfulClientFactory restfulClientFactory, CamelContext camelContext) {
        Integer connectionTimeout = endpointConfiguration.getConnectionTimeout();
        Integer socketTimeout = endpointConfiguration.getSocketTimeout();

        if (ObjectHelper.isNotEmpty(connectionTimeout)) {
            restfulClientFactory.setConnectTimeout(connectionTimeout);
        }
        if (ObjectHelper.isNotEmpty(socketTimeout)) {
            restfulClientFactory.setSocketTimeout(socketTimeout);
        }

        configureProxy(endpointConfiguration, restfulClientFactory, camelContext);
    }

    private static void configureProxy(FhirConfiguration endpointConfiguration, IRestfulClientFactory restfulClientFactory, CamelContext camelContext) {
        ServerValidationModeEnum validationMode = endpointConfiguration.getValidationMode();
        String proxyHost = endpointConfiguration.getProxyHost();
        Integer proxyPort = endpointConfiguration.getProxyPort();
        String proxyUser = endpointConfiguration.getProxyUser();
        String proxyPassword = endpointConfiguration.getProxyPassword();

        String camelProxyHost = camelContext.getGlobalOption("http.proxyHost");
        String camelProxyPort = camelContext.getGlobalOption("http.proxyPort");

        if (ObjectHelper.isNotEmpty(camelProxyHost) && ObjectHelper.isNotEmpty(camelProxyPort)) {
            restfulClientFactory.setProxy(camelProxyHost, Integer.parseInt(camelProxyPort));
        }
        if (ObjectHelper.isNotEmpty(proxyHost) && ObjectHelper.isNotEmpty(proxyPort)) {
            restfulClientFactory.setProxy(proxyHost, proxyPort);
        }
        if (ObjectHelper.isNotEmpty(proxyUser)) {
            restfulClientFactory.setProxyCredentials(proxyUser, proxyPassword);
        }
        if (ObjectHelper.isNotEmpty(validationMode)) {
            restfulClientFactory.setServerValidationMode(validationMode);
        }
    }

    private static void registerClientInterceptors(IGenericClient genericClient, FhirConfiguration endpointConfiguration) {
        String username = endpointConfiguration.getUsername();
        String password = endpointConfiguration.getPassword();
        String accessToken = endpointConfiguration.getAccessToken();
        String sessionCookie = endpointConfiguration.getSessionCookie();
        if (ObjectHelper.isNotEmpty(username)) {
            genericClient.registerInterceptor(new BasicAuthInterceptor(username, password));
        }
        if (ObjectHelper.isNotEmpty(accessToken)) {
            genericClient.registerInterceptor(new BearerTokenAuthInterceptor(accessToken));
        }
        if (endpointConfiguration.isLog()) {
            genericClient.registerInterceptor(new LoggingInterceptor(true));
        }
        if (endpointConfiguration.isCompress()) {
            genericClient.registerInterceptor(new GZipContentInterceptor());
        }
        if (ObjectHelper.isNotEmpty(sessionCookie)) {
            genericClient.registerInterceptor(new CookieInterceptor(sessionCookie));
        }
    }

    private static FhirContext getFhirContext(FhirConfiguration endpointConfiguration) {
        FhirContext context = endpointConfiguration.getFhirContext();
        if (context != null) {
            return context;
        }
        if (ObjectHelper.isEmpty(endpointConfiguration.getServerUrl())) {
            throw new RuntimeCamelException("The FHIR URL must be set!");
        }
        FhirVersionEnum fhirVersion = endpointConfiguration.getFhirVersion();
        return new FhirContext(fhirVersion);
    }
}
