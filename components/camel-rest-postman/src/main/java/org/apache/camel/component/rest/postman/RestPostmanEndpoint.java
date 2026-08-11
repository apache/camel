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

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumerAware;
import org.apache.camel.component.rest.postman.collection.PostmanCloudClient;
import org.apache.camel.component.rest.postman.collection.PostmanCollectionLoader;
import org.apache.camel.component.rest.postman.model.PostmanCollection;
import org.apache.camel.component.rest.postman.model.PostmanItem;
import org.apache.camel.component.rest.postman.support.PostmanRedactor;
import org.apache.camel.component.rest.postman.support.PostmanRequestBinding;
import org.apache.camel.component.rest.postman.support.PostmanRequestIndex;
import org.apache.camel.component.rest.postman.support.PostmanRequestMapper;
import org.apache.camel.spi.InternalProcessor;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestOpenApiConsumerFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.processor.RestBindingAdvice;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;
import static org.apache.camel.util.StringHelper.after;
import static org.apache.camel.util.StringHelper.before;

/**
 * To call and expose REST services using a Postman Collection as contract.
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "rest-postman", title = "REST Postman",
             syntax = "rest-postman:collectionSource#requestId", category = { Category.REST, Category.API },
             headersClass = RestPostmanConstants.class)
public class RestPostmanEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(RestPostmanEndpoint.class);

    public static final String[] DEFAULT_REST_POSTMAN_CONSUMER_COMPONENTS = new String[] { "platform-http" };

    /**
     * Parameters of the endpoint URI that were not consumed as options, kept so that they can be used as literal path
     * or query values.
     */
    Map<String, Object> parameters = Collections.emptyMap();

    @UriPath(description = "The Postman Collection to use. Either a resource URI of a Collection v2.1 JSON document"
                           + " (classpath:, file: or http:), or the uid of a collection to fetch from the Postman"
                           + " cloud, which requires postmanApiKey.",
             defaultValue = RestPostmanConfiguration.DEFAULT_COLLECTION_SOURCE,
             defaultValueNote = "By default loads the postman-collection.json file")
    private String collectionSource;

    @UriPath(description = "The request to invoke, identified by its id in the collection or by its slugified name,"
                           + " for example getUserById. Use a folder id to run every request in that folder, and"
                           + " leave it out to run the whole collection. Append a slash to force a folder match when"
                           + " a request and a folder share a name.",
             label = "producer")
    private String requestId;

    @UriParam
    private RestPostmanConfiguration configuration = new RestPostmanConfiguration();

    private RestPostmanProcessor postmanProcessor;

    public RestPostmanEndpoint(String uri, String remaining, RestPostmanComponent component,
                               Map<String, Object> parameters) {
        super(uri, component);

        if (remaining != null && remaining.contains("#")) {
            String fragment = after(remaining, "#");
            // an empty fragment means the same as no fragment: the whole collection
            requestId = fragment != null && !fragment.isEmpty() ? fragment : null;
            String source = before(remaining, "#");
            if (source != null && !source.isEmpty()) {
                collectionSource = source;
            }
        } else if (remaining != null && !remaining.isEmpty()) {
            if (looksLikeCollectionSource(remaining)) {
                collectionSource = remaining;
            } else {
                requestId = remaining;
            }
        }

        if (collectionSource == null) {
            collectionSource = component.getCollectionSource();
        }
        if (collectionSource == null) {
            collectionSource = RestPostmanConfiguration.DEFAULT_COLLECTION_SOURCE;
        }

        this.parameters = parameters;
        setExchangePattern(ExchangePattern.InOut);
    }

    /**
     * Distinguishes {@code rest-postman:my-api.json} from {@code rest-postman:getUserById} when no {@code #} was given.
     */
    private static boolean looksLikeCollectionSource(String remaining) {
        return remaining.endsWith(".json")
                || remaining.contains(":")
                || PostmanCollectionLoader.isCloudSource(remaining, PostmanCollectionLoader.SOURCE_TYPE_AUTO);
    }

    @Override
    public RestPostmanComponent getComponent() {
        return (RestPostmanComponent) super.getComponent();
    }

    @Override
    public boolean isLenientProperties() {
        // unknown URI parameters are literal path or query values rather than mistakes
        return true;
    }

    @Override
    public Producer createProducer() throws Exception {
        PostmanRequestIndex index = buildIndex();
        PostmanRequestIndex.Selection selection = index.resolve(requestId);

        List<PostmanRequestBinding> bindings = mapAll(selection.items());

        if (selection.single()) {
            PostmanRequestBinding binding = bindings.get(0);
            Endpoint delegate = createDelegateEndpoint(binding);
            return new RestPostmanProducer(delegate.createProducer(), binding.host() != null, binding);
        }

        // a folder or the whole collection: every request is run in turn, like Postman's collection runner
        List<RestPostmanRunnerProducer.PreparedRequest> prepared = new ArrayList<>(bindings.size());
        for (PostmanRequestBinding binding : bindings) {
            prepared.add(new RestPostmanRunnerProducer.PreparedRequest(
                    binding, createDelegateEndpoint(binding).createProducer()));
        }
        LOG.debug("Postman endpoint {} will run {} request(s) for {}", getEndpointUri(), prepared.size(),
                selection.description());
        return new RestPostmanRunnerProducer(this, prepared, configuration.isRunFailFast(), selection.description());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        PostmanCollection collection = loadCollection();
        PostmanRequestIndex index = PostmanRequestIndex.build(collection, configuration.getRequestFilter());
        PostmanRequestIndex.Selection selection = index.resolve(requestId);
        List<PostmanRequestBinding> bindings = mapAll(selection.items());

        String path = determineConsumerBasePath(bindings);

        RestPostmanProcessorStrategy strategy = getComponent().createProcessorStrategy();
        // these are endpoint level options, so they have to come from this endpoint's configuration rather than
        // from the component wide template the strategy was built with
        if (configuration.getMissingRequest() != null) {
            strategy.setMissingRequest(configuration.getMissingRequest());
        }
        if (configuration.getMockIncludePattern() != null) {
            strategy.setMockIncludePattern(configuration.getMockIncludePattern());
        }

        RestPostmanProcessor restPostmanProcessor = new RestPostmanProcessor(
                bindings, PostmanRedactor.redact(collection.getJson()), collectionSource, path,
                configuration.getApiContextPath(), configuration.isClientRequestValidation(), strategy);
        CamelContextAware.trySetCamelContext(restPostmanProcessor, getCamelContext());
        this.postmanProcessor = restPostmanProcessor;

        // the per-request binding advice replaces the stock one, exactly as the OpenAPI equivalent does
        if (processor instanceof InternalProcessor ip) {
            RestBindingAdvice advice = ip.getAdvice(RestBindingAdvice.class);
            if (advice != null) {
                ip.removeAdvice(advice);
            }
            ip.addAdvice(new RestPostmanProcessorAdvice(restPostmanProcessor));
        }

        Consumer consumer = createConsumerFor(path, restPostmanProcessor, processor);
        restPostmanProcessor.setConsumer(consumer);
        if (consumer instanceof PlatformHttpConsumerAware phca) {
            phca.registerAfterConfigured(restPostmanProcessor);
        }
        return consumer;
    }

    /**
     * A consumer serves one context path, but a collection can produce a different base path per request when its
     * folders use different base URLs. The first one wins, and the rest are reported, because silently serving them on
     * someone else's context path would be a confusing way to fail.
     */
    private String determineConsumerBasePath(List<PostmanRequestBinding> bindings) {
        if (bindings.isEmpty()) {
            return RestPostmanConfiguration.DEFAULT_BASE_PATH;
        }
        String path = bindings.get(0).basePath();
        Set<String> others = bindings.stream()
                .map(PostmanRequestBinding::basePath)
                .filter(other -> !path.equals(other))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!others.isEmpty()) {
            LOG.warn("Postman collection {} yields more than one base path ({} and {}). Serving everything under"
                     + " {}; set the basePath option to choose explicitly.",
                    collectionSource, path, String.join(", ", others), path);
        }
        return path;
    }

    private Consumer createConsumerFor(String basePath, RestPostmanProcessor restPostmanProcessor, Processor processor)
            throws Exception {
        RestOpenApiConsumerFactory factory = null;
        String cname = null;

        if (configuration.getConsumerComponentName() != null) {
            Object comp = getCamelContext().getRegistry().lookupByName(configuration.getConsumerComponentName());
            if (comp instanceof RestOpenApiConsumerFactory rcf) {
                factory = rcf;
            } else {
                comp = getCamelContext().getComponent(configuration.getConsumerComponentName());
                if (comp instanceof RestOpenApiConsumerFactory rcf) {
                    factory = rcf;
                }
            }
            if (factory == null) {
                if (comp != null) {
                    throw new IllegalArgumentException(
                            "Component " + configuration.getConsumerComponentName()
                                                       + " is not a RestOpenApiConsumerFactory");
                }
                throw new NoSuchBeanException(
                        configuration.getConsumerComponentName(), RestOpenApiConsumerFactory.class.getName());
            }
            cname = configuration.getConsumerComponentName();
        }

        if (factory == null) {
            for (String name : getCamelContext().getComponentNames()) {
                Component comp = getCamelContext().getComponent(name);
                if (comp instanceof RestOpenApiConsumerFactory rcf) {
                    factory = rcf;
                    cname = name;
                    break;
                }
            }
        }

        if (factory == null) {
            for (String name : DEFAULT_REST_POSTMAN_CONSUMER_COMPONENTS) {
                Object comp = getCamelContext().getComponent(name, true);
                if (comp instanceof RestOpenApiConsumerFactory rcf) {
                    LOG.debug("Auto discovered {} as RestOpenApiConsumerFactory", name);
                    factory = rcf;
                    cname = name;
                    break;
                }
            }
        }

        if (factory == null) {
            Set<RestOpenApiConsumerFactory> factories
                    = getCamelContext().getRegistry().findByType(RestOpenApiConsumerFactory.class);
            if (factories != null && factories.size() == 1) {
                factory = factories.iterator().next();
            }
        }

        if (factory == null) {
            throw new IllegalStateException(
                    "Cannot find RestOpenApiConsumerFactory in Registry or as a Component to use");
        }

        // fail closed: never start an unprotected consumer when oauthProfile is configured but the delegate
        // factory does not declare that its consumers enforce it
        if (isNotEmpty(configuration.getOauthProfile()) && !factory.supportsOAuthProfile()) {
            throw new IllegalArgumentException(
                    "The oauthProfile option is not supported by the resolved RestOpenApiConsumerFactory ("
                                               + factory.getClass().getName()
                                               + "); select a consumer component that enforces oauthProfile");
        }

        RestConfiguration config = CamelContextHelper.getRestConfiguration(getCamelContext(), cname);
        Map<String, Object> copy = new HashMap<>(parameters);
        if (isNotEmpty(configuration.getOauthProfile())) {
            copy.put("oauthProfile", configuration.getOauthProfile());
        }
        String contextPath = basePath;
        if (contextPath.equals(config.getContextPath())) {
            contextPath = "";
        }

        Consumer consumer = factory.createConsumer(getCamelContext(), processor, contextPath, config, copy);
        if (consumer instanceof PlatformHttpConsumerAware phca) {
            restPostmanProcessor.setPlatformHttpConsumer(phca);
        }
        configureConsumer(consumer);
        return consumer;
    }

    /**
     * Builds the {@code rest} endpoint that actually performs the call.
     * <p>
     * Every option that distinguishes one request from another goes into the URI string, because endpoints are cached
     * by URI and two requests differing only in, say, host must not share one (see CAMEL-24113).
     */
    private Endpoint createDelegateEndpoint(PostmanRequestBinding binding) {
        Endpoint delegate = getCamelContext().getEndpoint(buildDelegateUri(binding));
        delegate.configureProperties(determineEndpointParameters(binding));
        return delegate;
    }

    /**
     * Builds the URI of the delegate {@code rest} endpoint.
     * <p>
     * Every option that distinguishes one request from another is part of the URI, because endpoints are cached by URI
     * and two requests differing only in, say, host must not end up sharing one (CAMEL-24113).
     */
    String buildDelegateUri(PostmanRequestBinding binding) {
        String uri = "rest:" + binding.method() + ":" + binding.basePath() + ":" + binding.uriTemplate();

        StringBuilder query = new StringBuilder();
        appendQuery(query, "host", binding.host());
        appendQuery(query, "producerComponentName", configuration.getComponentName());
        appendQuery(query, "consumes", binding.consumes());
        appendQuery(query, "produces", binding.produces());
        if (binding.queryParameters() != null) {
            appendQuery(query, "queryParameters", UnsafeUriCharactersEncoder.encode(binding.queryParameters()));
        }
        if (!query.isEmpty()) {
            uri = uri + "?" + query;
        }
        return uri;
    }

    private static void appendQuery(StringBuilder query, String name, String value) {
        if (value == null) {
            return;
        }
        if (!query.isEmpty()) {
            query.append('&');
        }
        query.append(name).append('=').append(value);
    }

    private Map<String, Object> determineEndpointParameters(PostmanRequestBinding binding) {
        Map<String, Object> answer = new LinkedHashMap<>();
        if (binding.host() != null) {
            answer.put("host", binding.host());
        }
        if (configuration.getComponentName() != null) {
            answer.put("producerComponentName", configuration.getComponentName());
        }
        if (binding.consumes() != null) {
            answer.put("consumes", binding.consumes());
        }
        if (binding.produces() != null) {
            answer.put("produces", binding.produces());
        }
        if (binding.queryParameters() != null) {
            answer.put("queryParameters", binding.queryParameters());
        }

        Map<String, Object> nested = new LinkedHashMap<>();
        Map<String, Object> componentOptions = new LinkedHashMap<>();
        componentOptions.put("useGlobalSslContextParameters", configuration.isUseGlobalSslContextParameters());
        if (configuration.getSslContextParameters() != null) {
            componentOptions.put("sslContextParameters", configuration.getSslContextParameters());
        }
        nested.put("component", componentOptions);

        // leftover URI parameters are literal values, except where they name a path parameter, which would then be
        // sent both in the path and as a query parameter
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (!binding.defaultPathValues().containsKey(entry.getKey())
                    && !binding.uriTemplate().contains("{" + entry.getKey() + "}")) {
                nested.put(entry.getKey(), entry.getValue());
            }
        }
        answer.put("parameters", nested);
        return answer;
    }

    private PostmanRequestIndex buildIndex() {
        return PostmanRequestIndex.build(loadCollection(), configuration.getRequestFilter());
    }

    /**
     * Resolves the URI fragment and maps every selected request, without creating any producer. Exposed so that the
     * mapping can be asserted without an HTTP backend.
     */
    List<PostmanRequestBinding> resolveBindings() {
        return mapAll(buildIndex().resolve(requestId).items());
    }

    private List<PostmanRequestBinding> mapAll(List<PostmanItem> items) {
        PostmanRequestMapper mapper = new PostmanRequestMapper(
                getCamelContext(), configuration, configuration.variablesAsStrings(), resourceOrigin());
        List<PostmanRequestBinding> answer = new ArrayList<>(items.size());
        for (PostmanItem item : items) {
            answer.add(mapper.map(item));
        }
        return answer;
    }

    /**
     * Loads the collection, through the component wide cache so that many endpoints over one collection cause one read,
     * and for cloud sources one API call.
     */
    PostmanCollection loadCollection() {
        boolean cloud = PostmanCollectionLoader.isCloudSource(collectionSource, configuration.getCollectionSourceType());
        return getComponent().getCollectionCache().get(
                collectionSource, configuration.getPostmanApiKey(), configuration.getCollectionCacheTtl(),
                () -> cloud
                        ? PostmanCollectionLoader.loadFromCloud(createCloudClient(), collectionSource)
                        : PostmanCollectionLoader.loadFromResource(getCamelContext(), collectionSource));
    }

    private PostmanCloudClient createCloudClient() {
        PostmanCloudClient.validateApiUrl(configuration.getPostmanApiUrl());
        if (configuration.getPostmanApiKey() == null || configuration.getPostmanApiKey().isEmpty()) {
            throw new IllegalArgumentException(
                    "postmanApiKey is required to fetch collection " + collectionSource + " from the Postman cloud."
                                               + " Set collectionSourceType=resource if this is meant to be a local"
                                               + " file rather than a collection uid.");
        }
        return new PostmanCloudClient(
                configuration.getPostmanApiUrl(),
                configuration.getPostmanApiKey(),
                configuration.getPostmanApiKeyHeader(),
                Duration.ofMillis(configuration.getConnectTimeout()),
                Duration.ofMillis(configuration.getRequestTimeout()),
                resolveSslContext());
    }

    private SSLContext resolveSslContext() {
        try {
            if (configuration.getSslContextParameters() != null) {
                return configuration.getSslContextParameters().createSSLContext(getCamelContext());
            }
            if (configuration.isUseGlobalSslContextParameters()
                    && getCamelContext().getSSLContextParameters() != null) {
                return getCamelContext().getSSLContextParameters().createSSLContext(getCamelContext());
            }
        } catch (Exception e) {
            throw new RuntimeCamelException("Cannot create SSLContext for fetching the Postman collection", e);
        }
        return null;
    }

    /**
     * The scheme and authority of the collection resource when it was loaded over HTTP, usable as a last resort for the
     * target host.
     * <p>
     * Deliberately {@code null} for cloud sources: their origin is {@code api.getpostman.com}, which is emphatically
     * not the API the collection describes.
     */
    private String resourceOrigin() {
        if (PostmanCollectionLoader.isCloudSource(collectionSource, configuration.getCollectionSourceType())) {
            return null;
        }
        if (!collectionSource.startsWith("http://") && !collectionSource.startsWith("https://")) {
            return null;
        }
        try {
            URI uri = new URI(collectionSource);
            StringBuilder answer = new StringBuilder(uri.getScheme()).append("://").append(uri.getHost());
            if (uri.getPort() > 0) {
                answer.append(':').append(uri.getPort());
            }
            return answer.toString();
        } catch (Exception e) {
            LOG.debug("Cannot derive an origin from collection source {}", collectionSource, e);
            return null;
        }
    }

    public String getCollectionSource() {
        return collectionSource;
    }

    public void setCollectionSource(String collectionSource) {
        this.collectionSource = collectionSource;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public RestPostmanConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(RestPostmanConfiguration configuration) {
        this.configuration = configuration;
    }

    RestPostmanProcessor getPostmanProcessor() {
        return postmanProcessor;
    }
}
