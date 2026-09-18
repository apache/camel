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

    @UriParam(label = "common", enums = "auto,resource,cloud", defaultValue = "auto")
    @Metadata(description = "How to interpret collectionSource. With auto, a bare collection UUID or"
                            + " {ownerId}-{uuid} is fetched from the Postman cloud and anything else is resolved as a"
                            + " resource (classpath:, file:, http:). Use resource or cloud to decide explicitly.",
              defaultValue = "auto")
    private String collectionSourceType = "auto";

    @UriParam(label = "common")
    @Metadata(description = "API basePath, for example \"`/v2`\". Default is unset, if set overrides the value"
                            + " derived from the request URL in the collection.")
    private String basePath = "";

    @UriParam(label = "common", prefix = "variable.", multiValue = true)
    @Metadata(description = "Values for the {{variable}} placeholders used in the collection. These override the"
                            + " variables declared by the collection and its folders.")
    private Map<String, Object> variables;

    @UriParam(label = "common,advanced")
    @Metadata(description = "Whether to fail if a {{variable}} placeholder used by the selected request cannot be"
                            + " resolved. When false the placeholder is left as-is.")
    private boolean failOnUnresolvedVariable;

    @UriParam(label = "producer")
    @Metadata(description = "Scheme hostname and port to direct the HTTP requests to in the form of"
                            + " `http[s]://hostname[:port]`. If set overrides any value derived from the collection.")
    private String host;

    @UriParam(label = "producer,advanced")
    @Metadata(description = "Name of the Camel component that will perform the requests. The component must be"
                            + " present in Camel registry and it must implement RestProducerFactory service provider"
                            + " interface. If not set CLASSPATH is searched for single component that implements"
                            + " RestProducerFactory SPI.")
    private String componentName;

    @UriParam(label = "producer,advanced")
    @Metadata(description = "What payload type this component is capable of consuming. This equates to the value of"
                            + " the `Accept` HTTP header. A Postman collection does not describe responses, so unlike"
                            + " an OpenAPI specification there is nothing to infer this from and it is unset by"
                            + " default.")
    private String consumes;

    @UriParam(label = "producer,advanced")
    @Metadata(description = "What payload type this component is producing. This equates to the value of the"
                            + " `Content-Type` HTTP header. If not set it is inferred from the body mode of the"
                            + " request in the collection.")
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
    @Metadata(description = "Name of the Camel component that will service the requests. The component must be"
                            + " present in Camel registry and it must be able to service contract-first REST"
                            + " consumers, as platform-http does. If not set CLASSPATH is searched for a single"
                            + " component with that capability.")
    private String consumerComponentName;

    @UriParam(label = "consumer", enums = "fail,ignore,mock", defaultValue = "fail")
    @Metadata(description = "Whether the consumer should fail, ignore or return a mock response for requests in the"
                            + " collection that are not mapped to a corresponding route.",
              defaultValue = "fail")
    private String missingRequest;

    @UriParam(label = "consumer,advanced", defaultValue = "classpath:camel-mock/**")
    @Metadata(description = "Used for inclusive filtering of mock data from directories. The pattern is using"
                            + " Ant-path style pattern. Multiple patterns can be specified separated by comma."
                            + " Saved example responses in the collection are preferred over these files.",
              defaultValue = "classpath:camel-mock/**")
    private String mockIncludePattern = "classpath:camel-mock/**";

    @UriParam(label = "consumer")
    @Metadata(description = "Whether to enable validation of the client request. A Postman collection has no schemas,"
                            + " so this is a best-effort check of required headers, query parameters and body"
                            + " presence only.")
    private boolean clientRequestValidation;

    @UriParam(label = "consumer")
    @Metadata(description = "Sets the context-path to use for servicing the Postman collection document. The document"
                            + " is served with all auth blocks and all secret variables removed.")
    private String apiContextPath;

    @UriParam(label = "consumer,advanced")
    @Metadata(description = "Filters which requests of the collection are used, as comma separated Ant-style patterns"
                            + " matched against the folder qualified request id. Prefix a pattern with ! to exclude.")
    private String requestFilter;

    @UriParam(label = "consumer,security", displayName = "OAuth Profile")
    @Metadata(description = "The OAuth profile to use for authenticating the incoming requests. The profile is"
                            + " enforced by the consumer component servicing the requests.")
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
    @Metadata(description = "How long a loaded collection is cached, in milliseconds. Use -1 to cache for the"
                            + " lifetime of the component.",
              defaultValue = "-1")
    private long collectionCacheTtl = -1;

    @UriParam(label = "security")
    @Metadata(description = "Customize TLS parameters used by the component. If not set defaults to the TLS"
                            + " parameters set in the Camel context. These parameters are used both when fetching a"
                            + " collection from the Postman cloud and by the delegate producer.")
    private SSLContextParameters sslContextParameters;

    @UriParam(label = "security", defaultValue = "false")
    @Metadata(description = "Enable usage of global SSL context parameters.", defaultValue = "false")
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
