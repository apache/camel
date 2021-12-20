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

    public static IGenericClient createClient(FhirConfiguration config, CamelContext camelContext) {
        if (config.getClient() != null) {
            return config.getClient();
        }
        FhirContext fhirContext = getFhirContext(config);
        if (config.isDeferModelScanning()) {
            fhirContext.setPerformanceOptions(PerformanceOptionsEnum.DEFERRED_MODEL_SCANNING);
        }
        if (config.getClientFactory() != null) {
            fhirContext.setRestfulClientFactory(config.getClientFactory());
        }

        IRestfulClientFactory restfulClientFactory = fhirContext.getRestfulClientFactory();
        configureClientFactory(config, restfulClientFactory, camelContext);
        IGenericClient genericClient = fhirContext.newRestfulGenericClient(config.getServerUrl());
        genericClient.setPrettyPrint(config.isPrettyPrint());
        EncodingEnum encoding = config.getEncoding();
        SummaryEnum summary = config.getSummary();

        if (encoding != null) {
            genericClient.setEncoding(encoding);
        }
        if (summary != null) {
            genericClient.setSummary(summary);
        }
        if (config.isForceConformanceCheck()) {
            genericClient.forceConformanceCheck();
        }

        registerClientInterceptors(genericClient, config);
        return genericClient;
    }

    private static void configureClientFactory(
            FhirConfiguration config, IRestfulClientFactory restfulClientFactory, CamelContext camelContext) {
        Integer connectionTimeout = config.getConnectionTimeout();
        Integer socketTimeout = config.getSocketTimeout();

        if (ObjectHelper.isNotEmpty(connectionTimeout)) {
            restfulClientFactory.setConnectTimeout(connectionTimeout);
        }
        if (ObjectHelper.isNotEmpty(socketTimeout)) {
            restfulClientFactory.setSocketTimeout(socketTimeout);
        }

        configureProxy(config, restfulClientFactory, camelContext);
    }

    private static void configureProxy(
            FhirConfiguration config, IRestfulClientFactory restfulClientFactory, CamelContext camelContext) {
        ServerValidationModeEnum validationMode = config.getValidationMode();
        String proxyHost = config.getProxyHost();
        Integer proxyPort = config.getProxyPort();
        String proxyUser = config.getProxyUser();
        String proxyPassword = config.getProxyPassword();

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

    private static void registerClientInterceptors(IGenericClient genericClient, FhirConfiguration config) {
        String username = config.getUsername();
        String password = config.getPassword();
        String accessToken = config.getAccessToken();
        String sessionCookie = config.getSessionCookie();
        if (ObjectHelper.isNotEmpty(username)) {
            genericClient.registerInterceptor(new BasicAuthInterceptor(username, password));
        }
        if (ObjectHelper.isNotEmpty(accessToken)) {
            genericClient.registerInterceptor(new BearerTokenAuthInterceptor(accessToken));
        }
        if (config.isLog()) {
            genericClient.registerInterceptor(new LoggingInterceptor(true));
        }
        if (config.isCompress()) {
            genericClient.registerInterceptor(new GZipContentInterceptor());
        }
        if (ObjectHelper.isNotEmpty(sessionCookie)) {
            genericClient.registerInterceptor(new CookieInterceptor(sessionCookie));
        }
    }

    private static FhirContext getFhirContext(FhirConfiguration config) {
        FhirContext context = config.getFhirContext();
        if (context != null) {
            return context;
        }
        if (ObjectHelper.isEmpty(config.getServerUrl())) {
            throw new RuntimeCamelException("The FHIR URL must be set!");
        }
        FhirVersionEnum fhirVersion = config.getFhirVersion();
        return new FhirContext(fhirVersion);
    }
}
