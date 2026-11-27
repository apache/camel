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
package org.apache.camel.component.rest.postman;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.support.jsse.SSLContextParameters;

/**
 * Configuration shared by the {@code rest-postman} component and its endpoints.
 * <p>
 * This class deliberately does not generate a {@code toString}, because it holds the Postman API key.
 */
@Configurer(extended = true)
@UriParams
public class RestPostmanConfiguration implements Cloneable {

    public static final String DEFAULT_COLLECTION_SOURCE = "postman-collection.json";
    public static final String DEFAULT_POSTMAN_API_URL = "https://api.getpostman.com";
    public static final String DEFAULT_POSTMAN_API_KEY_HEADER = "X-Api-Key";
    public static final String DEFAULT_BASE_PATH = "/";
    public static final String RESOLVE_VARIABLES_FROM_PROPERTIES_AUTO = "auto";
    public static final String RESOLVE_VARIABLES_FROM_PROPERTIES_ENABLED = "enabled";
    public static final String RESOLVE_VARIABLES_FROM_PROPERTIES_DISABLED = "disabled";

    @UriParam(label = "common", enums = "auto,resource,cloud", defaultValue = "auto")
    @Metadata(description = "How to interpret collectionSource. With auto, a bare collection UUID or"
                            + " {ownerId}-{uuid} is fetched from the Postman cloud and anything else is resolved as a"
                            + " resource (classpath:, file:, http:). Use resource or cloud to decide explicitly.")
    private String collectionSourceType = "auto";

    @UriParam(label = "common")
    @Metadata(description = "The base path of the API, for example \"`/v2`\", in front of the path of every request,"
                            + " and the path the consumer serves the requests under. If not set, the context path of"
                            + " the REST configuration is used, or else the path that the host of the request URL"
                            + " carries, such as /v1 when {{baseUrl}} is https://api.example.com/v1.")
    private String basePath = "";

    @UriParam(label = "common", prefix = "variable.", multiValue = true)
    @Metadata(description = "Values for the {{variable}} placeholders used in the collection. These override the"
                            + " variables declared by the collection and its folders.")
    private Map<String, Object> variables;

    @UriParam(label = "common,advanced")
    @Metadata(description = "Whether to fail when a {{variable}} placeholder used by a selected request cannot be"
                            + " resolved. When false the placeholder is left as-is in headers and bodies, while one"
                            + " left in the URL, the query parameters, Accept or Content-Type fails anyway.")
    private boolean failOnUnresolvedVariable;

    @UriParam(label = "common,security", enums = "auto,enabled,disabled", defaultValue = "auto")
    @Metadata(description = "Whether a {{variable}} placeholder that neither the collection nor the variables option"
                            + " defines is resolved from Camel properties, which by default also cover JVM system"
                            + " properties and OS environment variables. With auto, this is done for a collection read"
                            + " from the classpath or the file system, and not for one fetched from the Postman cloud,"
                            + " over HTTP or through any other resource scheme, because whoever edits or serves such a"
                            + " collection could otherwise copy those values into an outgoing request. Use enabled or"
                            + " disabled to decide explicitly for any source.")
    private String resolveVariablesFromProperties = RESOLVE_VARIABLES_FROM_PROPERTIES_AUTO;

    @UriParam(label = "producer")
    @Metadata(description = "The scheme, host name and port to send the requests to, in the form"
                            + " `http[s]://hostname[:port]`. If set, it replaces the host of every request URL in the"
                            + " collection. If not set, the host comes from the request URL, then from the REST"
                            + " configuration, and for a collection loaded over HTTP from the address it was loaded"
                            + " from.")
    private String host;

    @UriParam(label = "producer,advanced")
    @Metadata(description = "Name of the Camel component that performs the HTTP requests, which must implement the"
                            + " RestProducerFactory SPI, as http, vertx-http, undertow and netty-http do. If not set, a"
                            + " component with that capability already in the Camel context or the registry is used,"
                            + " or else the first of http, vertx-http, undertow and netty-http on the classpath.")
    private String componentName;

    @UriParam(label = "producer,advanced")
    @Metadata(description = "The media type accepted in the response, sent as the `Accept` HTTP header. If not set,"
                            + " the Accept header of the request in the collection is used, when it has one. A Postman"
                            + " collection does not describe responses, so there is nothing else to infer it from.")
    private String consumes;

    @UriParam(label = "producer,advanced")
    @Metadata(description = "The media type of the request body, sent as the `Content-Type` HTTP header. If not set,"
                            + " the Content-Type header of the request in the collection is used, or else a type"
                            + " inferred from its body mode, such as application/json for a raw JSON body.")
    private String produces;

    @UriParam(label = "producer,advanced", enums = "placeholder,literal", defaultValue = "placeholder")
    @Metadata(description = "How to treat the query parameters declared in the collection. With placeholder the"
                            + " parameter names are bound to message headers and the values in the collection are"
                            + " ignored as sample data. With literal the values in the collection are sent as-is.",
              defaultValue = "placeholder")
    private String queryParameterMode = "placeholder";

    @UriParam(label = "producer", defaultValue = "true")
    @Metadata(description = "When the endpoint runs more than one request, that is when it selects a folder or the"
                            + " whole collection, whether to stop and fail on the first request that fails. When"
                            + " false every request is attempted and the failure is recorded in its result.",
              defaultValue = "true")
    private boolean runFailFast = true;

    @UriParam(label = "consumer,advanced")
    @Metadata(description = "Name of the Camel component that serves the HTTP requests, which must implement the"
                            + " RestOpenApiConsumerFactory SPI, as platform-http does. If not set, a component with"
                            + " that capability already in the Camel context is used, or else platform-http.")
    private String consumerComponentName;

    @UriParam(label = "consumer", enums = "fail,ignore,mock", defaultValue = "fail")
    @Metadata(description = "What the consumer does with a request of the collection that has no route consuming"
                            + " from the direct endpoint named after the request id, such as direct:getUserById. With fail the route does not start, with ignore a warning is"
                            + " logged and such a request goes on to the route of this endpoint as is, and with mock"
                            + " it is answered with a saved example response of the collection, or else a file found"
                            + " by mockIncludePattern. If not set, it is mock when the Camel profile is dev, and fail"
                            + " otherwise.",
              defaultValue = "fail")
    private String missingRequest;

    @UriParam(label = "consumer,advanced", defaultValue = "classpath:camel-mock/**")
    @Metadata(description = "Comma separated Ant-style patterns of the files that missingRequest=mock answers from,"
                            + " for a request without a saved example response in the collection. A file is used when"
                            + " its path without the extension ends with the request path, such as camel-mock/pets/1.json"
                            + " for GET /pets/1.",
              defaultValue = "classpath:camel-mock/**")
    private String mockIncludePattern = "classpath:camel-mock/**";

    @UriParam(label = "consumer")
    @Metadata(description = "Whether the consumer validates the incoming requests. A Postman collection has no"
                            + " schemas, so this is a best-effort check: the headers the request declares, the query"
                            + " parameters that have a value in the collection, a body when the collection has one,"
                            + " and the media types. It is also enabled by clientRequestValidation of the REST"
                            + " configuration.")
    private boolean clientRequestValidation;

    @UriParam(label = "consumer")
    @Metadata(description = "The path, below the base path, on which the consumer serves the collection document as"
                            + " JSON, for example /postman.json. The auth blocks are removed from the document and"
                            + " the values of secret variables are replaced. If not set, the document is not served.")
    private String apiContextPath;

    @UriParam(label = "consumer,advanced")
    @Metadata(description = "Filters which requests of the collection are used, by the consumer and the producer, as"
                            + " comma separated Ant-style patterns matched against the folder qualified slug of the"
                            + " request, such as users/getUserById. Prefix a pattern with ! to exclude; with only"
                            + " exclusions, every other request is kept.")
    private String requestFilter;

    @UriParam(label = "consumer,security", displayName = "OAuth Profile")
    @Metadata(description = "The OAuth profile to use for authenticating the incoming requests. The profile is"
                            + " enforced by the consumer component servicing the requests, and the route fails to"
                            + " start when that component cannot enforce one.")
    private String oauthProfile;

    @UriParam(label = "security", security = "secret")
    @Metadata(description = "The Postman API key used to fetch the collection from the Postman cloud. This"
                            + " credential authenticates against Postman itself and is never sent to the API the"
                            + " collection describes.",
              security = "secret")
    private String postmanApiKey;

    @UriParam(label = "security", defaultValue = DEFAULT_POSTMAN_API_KEY_HEADER)
    @Metadata(description = "The HTTP header used to send the Postman API key when fetching a collection.",
              defaultValue = DEFAULT_POSTMAN_API_KEY_HEADER)
    private String postmanApiKeyHeader = DEFAULT_POSTMAN_API_KEY_HEADER;

    @UriParam(label = "security,advanced", defaultValue = DEFAULT_POSTMAN_API_URL)
    @Metadata(description = "The base URL of the Postman API used to fetch collections. Must use https, except for"
                            + " localhost, because plain http would send the Postman API key in clear text.",
              defaultValue = DEFAULT_POSTMAN_API_URL)
    private String postmanApiUrl = DEFAULT_POSTMAN_API_URL;

    @UriParam(label = "security", enums = "ignore,header,fail", defaultValue = "ignore")
    @Metadata(description = "What to do with the auth block the collection declares for the target API. With ignore"
                            + " the block is not applied, and a warning names the type that was found. With header"
                            + " the basic, bearer and apikey types are applied as a static header or query parameter,"
                            + " and any other type fails at startup rather than silently sending no credential. With"
                            + " fail any auth block other than noauth is rejected.",
              defaultValue = "ignore")
    private String collectionAuth = "ignore";

    @UriParam(label = "advanced", defaultValue = "15000")
    @Metadata(description = "Connection timeout in milliseconds when fetching a collection from the Postman cloud.",
              defaultValue = "15000")
    private long connectTimeout = 15000;

    @UriParam(label = "advanced", defaultValue = "30000")
    @Metadata(description = "Request timeout in milliseconds when fetching a collection from the Postman cloud.",
              defaultValue = "30000")
    private long requestTimeout = 30000;

    @UriParam(label = "advanced", defaultValue = "-1")
    @Metadata(description = "How long, in milliseconds, a loaded collection stays in the cache of the component, so"
                            + " that endpoints over the same collection load it once. A negative value keeps it until"
                            + " the component stops. The collection is read when a producer or consumer is created, so"
                            + " a running endpoint does not pick up changes.",
              defaultValue = "-1")
    private long collectionCacheTtl = -1;

    @UriParam(label = "security")
    @Metadata(description = "The TLS configuration used when fetching a collection from the Postman cloud, and set on"
                            + " the component that performs the requests when this endpoint creates it, as one already"
                            + " in the Camel context is not changed. If not set, see"
                            + " useGlobalSslContextParameters.")
    private SSLContextParameters sslContextParameters;

    @UriParam(label = "security", defaultValue = "false")
    @Metadata(description = "Whether to use the global SSL context parameters of the Camel context when"
                            + " sslContextParameters is not set, when fetching a collection from the Postman cloud and"
                            + " for the component that performs the requests when this endpoint creates it.",
              defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public String getCollectionSourceType() {
        return collectionSourceType;
    }

    public void setCollectionSourceType(String collectionSourceType) {
        this.collectionSourceType = collectionSourceType;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public boolean isFailOnUnresolvedVariable() {
        return failOnUnresolvedVariable;
    }

    public void setFailOnUnresolvedVariable(boolean failOnUnresolvedVariable) {
        this.failOnUnresolvedVariable = failOnUnresolvedVariable;
    }

    public String getResolveVariablesFromProperties() {
        return resolveVariablesFromProperties;
    }

    public void setResolveVariablesFromProperties(String resolveVariablesFromProperties) {
        this.resolveVariablesFromProperties = resolveVariablesFromProperties;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = RestPostmanHelper.isHostParam(host);
    }

    public String getComponentName() {
        return componentName;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    public String getConsumes() {
        return consumes;
    }

    public void setConsumes(String consumes) {
        this.consumes = RestPostmanHelper.isMediaRange(consumes, "consumes");
    }

    public String getProduces() {
        return produces;
    }

    public void setProduces(String produces) {
        this.produces = RestPostmanHelper.isMediaRange(produces, "produces");
    }

    public String getQueryParameterMode() {
        return queryParameterMode;
    }

    public void setQueryParameterMode(String queryParameterMode) {
        this.queryParameterMode = queryParameterMode;
    }

    public boolean isRunFailFast() {
        return runFailFast;
    }

    public void setRunFailFast(boolean runFailFast) {
        this.runFailFast = runFailFast;
    }

    public String getConsumerComponentName() {
        return consumerComponentName;
    }

    public void setConsumerComponentName(String consumerComponentName) {
        this.consumerComponentName = consumerComponentName;
    }

    public String getMissingRequest() {
        return missingRequest;
    }

    public void setMissingRequest(String missingRequest) {
        this.missingRequest = missingRequest;
    }

    public String getMockIncludePattern() {
        return mockIncludePattern;
    }

    public void setMockIncludePattern(String mockIncludePattern) {
        this.mockIncludePattern = mockIncludePattern;
    }

    public boolean isClientRequestValidation() {
        return clientRequestValidation;
    }

    public void setClientRequestValidation(boolean clientRequestValidation) {
        this.clientRequestValidation = clientRequestValidation;
    }

    public String getApiContextPath() {
        return apiContextPath;
    }

    public void setApiContextPath(String apiContextPath) {
        this.apiContextPath = apiContextPath;
    }

    public String getRequestFilter() {
        return requestFilter;
    }

    public void setRequestFilter(String requestFilter) {
        this.requestFilter = requestFilter;
    }

    public String getOauthProfile() {
        return oauthProfile;
    }

    public void setOauthProfile(String oauthProfile) {
        this.oauthProfile = oauthProfile;
    }

    public String getPostmanApiKey() {
        return postmanApiKey;
    }

    public void setPostmanApiKey(String postmanApiKey) {
        this.postmanApiKey = postmanApiKey;
    }

    public String getPostmanApiKeyHeader() {
        return postmanApiKeyHeader;
    }

    public void setPostmanApiKeyHeader(String postmanApiKeyHeader) {
        this.postmanApiKeyHeader = postmanApiKeyHeader;
    }

    public String getPostmanApiUrl() {
        return postmanApiUrl;
    }

    public void setPostmanApiUrl(String postmanApiUrl) {
        this.postmanApiUrl = postmanApiUrl;
    }

    public String getCollectionAuth() {
        return collectionAuth;
    }

    public void setCollectionAuth(String collectionAuth) {
        this.collectionAuth = collectionAuth;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public long getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public long getCollectionCacheTtl() {
        return collectionCacheTtl;
    }

    public void setCollectionCacheTtl(long collectionCacheTtl) {
        this.collectionCacheTtl = collectionCacheTtl;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public boolean isUseGlobalSslContextParameters() {
        return useGlobalSslContextParameters;
    }

    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    /**
     * The endpoint level variable overrides, as plain strings.
     */
    public Map<String, String> variablesAsStrings() {
        Map<String, String> answer = new LinkedHashMap<>();
        if (variables != null) {
            variables.forEach((key, value) -> answer.put(key, value != null ? value.toString() : ""));
        }
        return answer;
    }

    public RestPostmanConfiguration copy() {
        try {
            return (RestPostmanConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
