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
package org.apache.camel.component.rest.postman.support;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.component.rest.postman.RestPostmanConfiguration;
import org.apache.camel.component.rest.postman.RestPostmanHelper;
import org.apache.camel.component.rest.postman.model.PostmanAuth;
import org.apache.camel.component.rest.postman.model.PostmanBody;
import org.apache.camel.component.rest.postman.model.PostmanItem;
import org.apache.camel.component.rest.postman.model.PostmanKeyValue;
import org.apache.camel.component.rest.postman.model.PostmanRequest;
import org.apache.camel.component.rest.postman.model.PostmanUrl;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns a {@link PostmanItem} into a {@link PostmanRequestBinding}.
 * <p>
 * This is the one place where the shape of a Postman request is translated into the shape the {@code rest} component
 * expects, so both producers and the contract-first consumer stay consistent with each other.
 */
public final class PostmanRequestMapper {

    public static final String QUERY_MODE_LITERAL = "literal";

    public static final String COLLECTION_AUTH_IGNORE = "ignore";
    public static final String COLLECTION_AUTH_HEADER = "header";
    public static final String COLLECTION_AUTH_FAIL = "fail";

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    private static final Logger LOG = LoggerFactory.getLogger(PostmanRequestMapper.class);

    private final CamelContext camelContext;
    private final RestPostmanConfiguration configuration;
    private final Map<String, String> endpointVariables;
    private final String resourceOrigin;

    /**
     * Auth types already reported, so that a hundred requests sharing a collection level auth block produce one warning
     * rather than a hundred.
     */
    private final Set<String> reportedAuthTypes = new LinkedHashSet<>();

    /**
     * @param resourceOrigin the scheme and authority of the collection resource when it was loaded over HTTP, used as a
     *                       last resort for the target host. Must be {@code null} for cloud sources, whose origin is
     *                       the Postman API rather than the API being called.
     */
    public PostmanRequestMapper(CamelContext camelContext, RestPostmanConfiguration configuration,
                                Map<String, String> endpointVariables, String resourceOrigin) {
        this.camelContext = camelContext;
        this.configuration = configuration;
        this.endpointVariables = endpointVariables;
        this.resourceOrigin = resourceOrigin;
    }

    public PostmanRequestBinding map(PostmanItem item) {
        PostmanRequest request = item.getRequest();
        String description = item.describe();

        Map<String, String> scope = new LinkedHashMap<>(item.getScopeVariables());
        scope.putAll(endpointVariables);
        PostmanVariableResolver resolver
                = new PostmanVariableResolver(scope, camelContext, configuration.isFailOnUnresolvedVariable());

        String method = RestPostmanHelper.validateMethod(request.getMethod(), description);
        PostmanUrl url = request.getUrl();

        Origin origin = resolveOrigin(url, resolver, description);
        String basePath = resolveBasePath(origin.basePathSegments(), description);
        String uriTemplate = resolveUriTemplate(url, resolver, description);

        Map<String, String> staticHeaders = new LinkedHashMap<>();
        List<String> queryParts = new ArrayList<>();
        collectQueryParameters(url, resolver, description, queryParts);

        applyCollectionAuth(item, resolver, description, staticHeaders, queryParts);
        collectStaticHeaders(request, resolver, description, staticHeaders);

        Map<String, String> defaultPathValues = new LinkedHashMap<>();
        for (PostmanKeyValue variable : url.getPathVariables()) {
            if (!variable.disabled() && variable.value() != null) {
                defaultPathValues.put(variable.key(), resolver.resolve(variable.value(), description));
            }
        }

        return new PostmanRequestBinding(
                item,
                method,
                origin.host(),
                basePath,
                uriTemplate,
                queryParts.isEmpty() ? null : String.join("&", queryParts),
                resolveConsumes(request),
                resolveProduces(request),
                staticHeaders,
                defaultPathValues,
                resolveCollectionBody(request, resolver, description));
    }

    /**
     * Works out the target host and any path prefix that came with it.
     * <p>
     * The interesting case is that {@code url.host} is normally a bare {@code {{baseUrl}}}, and a Postman base URL
     * routinely expands to a complete URL such as {@code https://api.example.com/v1}. So whatever the host expands to
     * is re-parsed, and any path it carries becomes the base path.
     */
    private Origin resolveOrigin(PostmanUrl url, PostmanVariableResolver resolver, String description) {
        if (configuration.getHost() != null) {
            return new Origin(configuration.getHost(), List.of());
        }

        String rawHost = resolver.resolve(url.getHost(), description);
        String protocol = resolver.resolve(url.getProtocol(), description);
        String port = resolver.resolve(url.getPort(), description);
        List<String> basePathSegments = List.of();

        if (rawHost != null && !rawHost.isBlank()) {
            PostmanUrl expanded = PostmanUrl.parse(rawHost);
            if (expanded.getProtocol() != null) {
                protocol = expanded.getProtocol();
            }
            if (expanded.getPort() != null) {
                port = expanded.getPort();
            }
            if (!expanded.getPathSegments().isEmpty()) {
                basePathSegments = expanded.getPathSegments();
            }
            rawHost = expanded.getHost();
        }

        if (rawHost == null || rawHost.isBlank()) {
            String fromRestConfiguration = RestPostmanHelper.hostFrom(restConfiguration());
            if (fromRestConfiguration != null) {
                return new Origin(fromRestConfiguration, basePathSegments);
            }
            if (resourceOrigin != null) {
                return new Origin(resourceOrigin, basePathSegments);
            }
            return new Origin(null, basePathSegments);
        }

        if (protocol == null || protocol.isBlank()) {
            // Postman itself defaults to https; loopback hosts are almost always plain http in practice
            protocol = LOCAL_HOSTS.contains(rawHost.toLowerCase(Locale.ROOT)) ? "http" : "https";
        }

        StringBuilder host = new StringBuilder(protocol).append("://").append(rawHost);
        if (port != null && !port.isBlank()) {
            host.append(':').append(port);
        }
        return new Origin(RestPostmanHelper.isHostParam(host.toString()), basePathSegments);
    }

    private String resolveBasePath(List<String> derivedSegments, String description) {
        if (configuration.getBasePath() != null && !configuration.getBasePath().isEmpty()) {
            return normalizePath(configuration.getBasePath());
        }
        RestConfiguration restConfiguration = restConfiguration();
        if (restConfiguration != null && restConfiguration.getContextPath() != null
                && !restConfiguration.getContextPath().isEmpty()) {
            return normalizePath(restConfiguration.getContextPath());
        }
        if (derivedSegments.isEmpty()) {
            return RestPostmanConfiguration.DEFAULT_BASE_PATH;
        }
        List<String> encoded = new ArrayList<>(derivedSegments.size());
        for (String segment : derivedSegments) {
            encoded.add(UnsafeUriCharactersEncoder.encode(
                    RestPostmanHelper.validatePathSegment(segment, description)));
        }
        return "/" + String.join("/", encoded);
    }

    private String resolveUriTemplate(PostmanUrl url, PostmanVariableResolver resolver, String description) {
        List<String> segments = new ArrayList<>();
        for (String segment : url.getPathSegments()) {
            String resolved = resolver.resolve(segment, description);
            if (resolved == null || resolved.isEmpty()) {
                continue;
            }
            if (resolved.startsWith(":")) {
                // a Postman path parameter becomes the placeholder camel-rest resolves per exchange
                segments.add("{" + resolved.substring(1) + "}");
            } else {
                segments.add(UnsafeUriCharactersEncoder.encode(
                        RestPostmanHelper.validatePathSegment(resolved, description)));
            }
        }
        return "/" + String.join("/", segments);
    }

    private void collectQueryParameters(
            PostmanUrl url, PostmanVariableResolver resolver, String description,
            List<String> queryParts) {
        boolean literal = QUERY_MODE_LITERAL.equals(configuration.getQueryParameterMode());
        for (PostmanKeyValue param : url.getQueryParams()) {
            if (param.disabled()) {
                continue;
            }
            String name = resolver.resolve(param.key(), description);
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (literal && param.hasValue()) {
                String value = resolver.resolve(param.value(), description);
                queryParts.add(name + "=" + UnsafeUriCharactersEncoder.encode(value));
            } else {
                // the value in the collection is sample data, so bind the name to a header instead
                queryParts.add(RestPostmanHelper.queryParameterExpression(name, false));
            }
        }
    }

    private void collectStaticHeaders(
            PostmanRequest request, PostmanVariableResolver resolver, String description,
            Map<String, String> staticHeaders) {
        for (PostmanKeyValue header : request.getHeaders()) {
            if (header.disabled()) {
                continue;
            }
            // these two are carried as the consumes/produces options rather than as static headers
            if ("Content-Type".equalsIgnoreCase(header.key()) || "Accept".equalsIgnoreCase(header.key())) {
                continue;
            }
            staticHeaders.putIfAbsent(header.key(), resolver.resolve(header.value(), description));
        }
    }

    private String resolveConsumes(PostmanRequest request) {
        if (configuration.getConsumes() != null) {
            return configuration.getConsumes();
        }
        // a collection records no response schemas, so there is nothing to infer an Accept header from
        return request.getHeader("Accept");
    }

    private String resolveProduces(PostmanRequest request) {
        if (configuration.getProduces() != null) {
            return configuration.getProduces();
        }
        String declared = request.getHeader("Content-Type");
        if (declared != null) {
            return declared;
        }
        PostmanBody body = request.getBody();
        return body != null ? body.inferContentType() : null;
    }

    /**
     * Renders the body written in the collection, for the case where a whole folder or collection is run and the
     * exchange body cannot stand in for every request.
     *
     * @return the body, or {@code null} when there is none or it cannot be rendered
     */
    private String resolveCollectionBody(
            PostmanRequest request, PostmanVariableResolver resolver,
            String description) {
        PostmanBody body = request.getBody();
        if (body == null) {
            return null;
        }
        String mode = body.getMode();
        if (mode == null) {
            return null;
        }
        switch (mode) {
            case PostmanBody.MODE_RAW:
            case PostmanBody.MODE_GRAPHQL:
                return resolver.resolve(body.getRaw(), description);
            case PostmanBody.MODE_URLENCODED:
                return renderFormBody(body, resolver, description);
            case PostmanBody.MODE_FORMDATA:
            case PostmanBody.MODE_FILE:
            default:
                // multipart and file bodies cannot be rebuilt from the collection alone: a file body only records a
                // path on the machine of whoever authored the collection, which must never be read
                LOG.warn("Postman request {} uses a {} body, which cannot be reconstructed from the collection."
                         + " The message body of the exchange is sent instead.",
                        description, mode);
                return null;
        }
    }

    private String renderFormBody(PostmanBody body, PostmanVariableResolver resolver, String description) {
        List<String> parts = new ArrayList<>();
        for (PostmanKeyValue field : body.getFormFields()) {
            if (field.disabled()) {
                continue;
            }
            String value = resolver.resolve(field.value(), description);
            parts.add(URLEncoder.encode(field.key(), StandardCharsets.UTF_8)
                      + "=" + URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8));
        }
        return parts.isEmpty() ? null : String.join("&", parts);
    }

    /**
     * Applies the collection's own auth block to the outgoing request, according to {@code collectionAuth}.
     * <p>
     * This credential is entirely separate from {@code postmanApiKey}: this one authenticates against the API the
     * collection describes, the other one authenticates against Postman in order to download the collection.
     */
    private void applyCollectionAuth(
            PostmanItem item, PostmanVariableResolver resolver, String description,
            Map<String, String> staticHeaders, List<String> queryParts) {
        PostmanAuth auth = item.getEffectiveAuth();
        if (auth == null || auth.isNoAuth()) {
            return;
        }
        String mode = configuration.getCollectionAuth();

        if (COLLECTION_AUTH_FAIL.equals(mode)) {
            throw new IllegalArgumentException(
                    "Postman request " + description + " declares " + auth.getType() + " authentication, and"
                                               + " collectionAuth=fail rejects any auth block. Configure"
                                               + " authentication on the delegate HTTP component instead.");
        }
        if (COLLECTION_AUTH_IGNORE.equals(mode)) {
            if (reportedAuthTypes.add(auth.getType())) {
                LOG.warn("Postman collection declares {} authentication, which is not applied because"
                         + " collectionAuth=ignore. Set collectionAuth=header to apply it, or configure"
                         + " authentication on the delegate HTTP component.",
                        auth.getType());
            }
            return;
        }

        if (!auth.isSupported()) {
            throw new IllegalArgumentException(
                    "Postman request " + description + " declares " + auth.getType() + " authentication, which"
                                               + " cannot be reproduced as a static header because it requires"
                                               + " per-request signing or a token exchange. Configure it on the"
                                               + " delegate HTTP component, or set collectionAuth=ignore.");
        }

        switch (auth.getType()) {
            case PostmanAuth.TYPE_BASIC -> {
                String username = resolver.resolve(auth.getParameterOrDefault("username", ""), description);
                String password = resolver.resolve(auth.getParameterOrDefault("password", ""), description);
                String encoded = Base64.getEncoder()
                        .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
                staticHeaders.put("Authorization", "Basic " + encoded);
            }
            case PostmanAuth.TYPE_BEARER -> {
                String token = resolver.resolve(auth.getParameterOrDefault("token", ""), description);
                staticHeaders.put("Authorization", "Bearer " + token);
            }
            case PostmanAuth.TYPE_APIKEY -> {
                String key = resolver.resolve(auth.getParameterOrDefault("key", "Authorization"), description);
                String value = resolver.resolve(auth.getParameterOrDefault("value", ""), description);
                String in = auth.getParameterOrDefault("in", "header");
                if ("query".equalsIgnoreCase(in)) {
                    queryParts.add(key + "=" + UnsafeUriCharactersEncoder.encode(value));
                } else {
                    staticHeaders.put(key, value);
                }
            }
            default -> throw new IllegalStateException("Unhandled supported auth type: " + auth.getType());
        }
    }

    private RestConfiguration restConfiguration() {
        return CamelContextHelper.getRestConfiguration(camelContext, null, configuration.getComponentName());
    }

    private static String normalizePath(String path) {
        String answer = path.trim();
        if (!answer.startsWith("/")) {
            answer = "/" + answer;
        }
        while (answer.length() > 1 && answer.endsWith("/")) {
            answer = answer.substring(0, answer.length() - 1);
        }
        return answer;
    }

    /**
     * A target host together with any path prefix recovered from it.
     */
    private record Origin(String host, List<String> basePathSegments) {
    }
}
