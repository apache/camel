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
package org.apache.camel.component.fhir;

import java.util.List;
import java.util.Map;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.IInterceptorService;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.model.primitive.UriDt;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.RequestFormatParamStyleEnum;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.SummaryEnum;
import ca.uhn.fhir.rest.client.api.Header;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IHttpClient;
import ca.uhn.fhir.rest.client.api.IRestfulClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.gclient.ICreate;
import ca.uhn.fhir.rest.gclient.IDelete;
import ca.uhn.fhir.rest.gclient.IFetchConformanceUntyped;
import ca.uhn.fhir.rest.gclient.IGetPage;
import ca.uhn.fhir.rest.gclient.IHistory;
import ca.uhn.fhir.rest.gclient.IMeta;
import ca.uhn.fhir.rest.gclient.IOperation;
import ca.uhn.fhir.rest.gclient.IPatch;
import ca.uhn.fhir.rest.gclient.IRead;
import ca.uhn.fhir.rest.gclient.ITransaction;
import ca.uhn.fhir.rest.gclient.IUntypedQuery;
import ca.uhn.fhir.rest.gclient.IUpdate;
import ca.uhn.fhir.rest.gclient.IValidate;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.fhir.internal.FhirApiCollection;
import org.apache.camel.component.fhir.internal.FhirCreateApiMethod;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link FhirConfiguration} APIs.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = "apache.org",
                          disabledReason = "Apache CI nodes are too resource constrained for this test - see CAMEL-19659")
public class FhirCustomClientConfigurationIT extends AbstractFhirTestSupport {

    private static final String PATH_PREFIX = FhirApiCollection.getCollection().getApiName(FhirCreateApiMethod.class).getName();

    private static final String TEST_URI_CUSTOM_CLIENT
            = "fhir://" + PATH_PREFIX + "/resource?inBody=resourceAsString&client=#customClient";

    private static final String TEST_URI_CUSTOM_CLIENT_FACTORY
            = "fhir://" + PATH_PREFIX + "/resource?inBody=resourceAsString&clientFactory=#customClientFactory&serverUrl=foobar";

    @BindToRegistry("customClient")
    private CustomClient client = new CustomClient();

    @BindToRegistry("customClientFactory")
    private CustomClientFactory clientFactory = new CustomClientFactory();

    @Test
    public void testConfigurationWithCustomClient() {
        FhirEndpoint endpoint = getMandatoryEndpoint(TEST_URI_CUSTOM_CLIENT, FhirEndpoint.class);
        IGenericClient client = endpoint.getClient();
        assertTrue(client instanceof CustomClient);
    }

    @Test
    public void testConfigurationWithCustomFactory() {
        FhirEndpoint endpoint = getMandatoryEndpoint(TEST_URI_CUSTOM_CLIENT_FACTORY, FhirEndpoint.class);
        IGenericClient client = endpoint.getClient();
        assertTrue(client instanceof CustomClient);
    }

    @Override
    public void cleanFhirServerState() {
        // do nothing
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct://CONFIGURATION_CUSTOM_CLIENT").to(TEST_URI_CUSTOM_CLIENT);
                from("direct://CONFIGURATION_CUSTOM_CLIENT_FACTORY").to(TEST_URI_CUSTOM_CLIENT_FACTORY);
            }
        };
    }

    private class CustomClientFactory implements IRestfulClientFactory {

        @Override
        public int getConnectionRequestTimeout() {
            return 0;
        }

        @Override
        public int getConnectTimeout() {
            return 0;
        }

        @Override
        public IHttpClient getHttpClient(
                StringBuilder theUrl, Map<String, List<String>> theIfNoneExistParams, String theIfNoneExistString,
                RequestTypeEnum theRequestType, List<Header> theHeaders) {
            return null;
        }

        @Override
        public ServerValidationModeEnum getServerValidationModeEnum() {
            return null;
        }

        @Override
        public ServerValidationModeEnum getServerValidationMode() {
            return null;
        }

        @Override
        public int getSocketTimeout() {
            return 0;
        }

        @Override
        public int getPoolMaxTotal() {
            return 0;
        }

        @Override
        public int getPoolMaxPerRoute() {
            return 0;
        }

        @Override
        public <T extends IRestfulClient> T newClient(Class<T> theClientType, String theServerBase) {
            return null;
        }

        @Override
        public IGenericClient newGenericClient(String theServerBase) {
            return new CustomClient();
        }

        @Override
        public void setConnectionRequestTimeout(int theConnectionRequestTimeout) {

        }

        @Override
        public void setConnectTimeout(int theConnectTimeout) {

        }

        @Override
        public <T> void setHttpClient(T theHttpClient) {

        }

        @Override
        public void setProxy(String theHost, Integer thePort) {

        }

        @Override
        public void setProxyCredentials(String theUsername, String thePassword) {

        }

        @Override
        public void setServerValidationModeEnum(ServerValidationModeEnum theServerValidationMode) {

        }

        @Override
        public void setServerValidationMode(ServerValidationModeEnum theServerValidationMode) {

        }

        @Override
        public void setSocketTimeout(int theSocketTimeout) {

        }

        @Override
        public void setPoolMaxTotal(int thePoolMaxTotal) {

        }

        @Override
        public void setPoolMaxPerRoute(int thePoolMaxPerRoute) {

        }

        @Override
        public void validateServerBase(String theServerBase, IHttpClient theHttpClient, IRestfulClient theClient) {

        }

        @Override
        public void validateServerBaseIfConfiguredToDoSo(
                String theServerBase, IHttpClient theHttpClient, IRestfulClient theClient) {

        }
    }

    private class CustomClient implements IGenericClient {

        @Override
        public IFetchConformanceUntyped capabilities() {
            return null;
        }

        @Override
        public ICreate create() {
            return null;
        }

        @Override
        public IDelete delete() {
            return null;
        }

        @Override
        public IFetchConformanceUntyped fetchConformance() {
            return null;
        }

        @Override
        public void forceConformanceCheck() throws FhirClientConnectionException {

        }

        @Override
        public IHistory history() {
            return null;
        }

        @Override
        public IGetPage loadPage() {
            return null;
        }

        @Override
        public IMeta meta() {
            return null;
        }

        @Override
        public IOperation operation() {
            return null;
        }

        @Override
        public IPatch patch() {
            return null;
        }

        @Override
        public IRead read() {
            return null;
        }

        @Override
        public <T extends IBaseResource> T read(Class<T> theType, String theId) {
            return null;
        }

        @Override
        public <T extends IBaseResource> T read(Class<T> theType, UriDt theUrl) {
            return null;
        }

        @Override
        public IBaseResource read(UriDt theUrl) {
            return null;
        }

        @Override
        public void registerInterceptor(Object o) {

        }

        @Override
        public IInterceptorService getInterceptorService() {
            return null;
        }

        @Override
        public void setInterceptorService(IInterceptorService theInterceptorService) {

        }

        @Override
        public <T extends IBaseResource> T fetchResourceFromUrl(Class<T> theResourceType, String theUrl) {
            return null;
        }

        @Override
        public EncodingEnum getEncoding() {
            return null;
        }

        @Override
        public FhirContext getFhirContext() {
            return null;
        }

        @Override
        public IHttpClient getHttpClient() {
            return null;
        }

        @Override
        public String getServerBase() {
            return null;
        }

        @Override
        public void setEncoding(EncodingEnum theEncoding) {

        }

        @Override
        public void setPrettyPrint(Boolean thePrettyPrint) {

        }

        @Override
        public void setSummary(SummaryEnum theSummary) {

        }

        @Override
        public <T extends IBaseBundle> IUntypedQuery<T> search() {
            return null;
        }

        @Override
        public void setLogRequestAndResponse(boolean theLogRequestAndResponse) {

        }

        @Override
        public ITransaction transaction() {
            return null;
        }

        @Override
        public void unregisterInterceptor(Object o) {

        }

        @Override
        public void setFormatParamStyle(RequestFormatParamStyleEnum requestFormatParamStyleEnum) {

        }

        @Override
        public IUpdate update() {
            return null;
        }

        @Override
        public MethodOutcome update(IdDt theId, IBaseResource theResource) {
            return null;
        }

        @Override
        public MethodOutcome update(String theId, IBaseResource theResource) {
            return null;
        }

        @Override
        public IValidate validate() {
            return null;
        }

        @Override
        public MethodOutcome validate(IBaseResource theResource) {
            return null;
        }

        @Override
        public <T extends IBaseResource> T vread(Class<T> theType, IdDt theId) {
            return null;
        }

        @Override
        public <T extends IBaseResource> T vread(Class<T> theType, String theId, String theVersionId) {
            return null;
        }
    }
}
