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

package org.apache.camel.component.rest.openapi;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;
import static org.apache.camel.util.StringHelper.notEmpty;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.ContentTypeAware;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

public final class RestOpenApiHelper {

    private static final Pattern HOST_PATTERN = Pattern.compile("https?://[^:]+(:\\d+)?", Pattern.CASE_INSENSITIVE);
    private static final List<String> YAML_CONTENT_TYPES =
            Arrays.asList("application/yaml", "application/yml", "text/yaml", "text/yml", "text/x-yaml");

    private RestOpenApiHelper() {
        // utility class
    }

    public static String isMediaRange(final String given, final String name) {
        return notEmpty(given, name);
    }

    /**
     * Determines if a {@link Resource} contains YAML content.
     *
     * @param  resource The resource to inspect
     * @return          {@code true} if the resource has YAML content, otherwise {@code false}
     */
    public static boolean isYamlResource(Resource resource) {
        Objects.requireNonNull(resource, "resource cannot be null");

        if (resource instanceof ContentTypeAware) {
            String contentType = ((ContentTypeAware) resource).getContentType();
            return isYamlResourceLocation(resource.getLocation()) || isYamlContentType(contentType);
        }

        return isYamlResourceLocation(resource.getLocation());
    }

    static String isHostParam(final String given) {
        final String hostUri = StringHelper.notEmpty(given, "host");

        final Matcher matcher = HOST_PATTERN.matcher(given);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "host must be an absolute URI (e.g. http://api.example.com), given: `" + hostUri + "`");
        }

        return hostUri;
    }

    private static boolean isYamlContentType(String contentType) {
        if (ObjectHelper.isEmpty(contentType)) {
            return false;
        }
        return Stream.of(contentType.split(";")).map(String::trim).anyMatch(YAML_CONTENT_TYPES::contains);
    }

    private static boolean isYamlResourceLocation(String location) {
        if (ObjectHelper.isEmpty(location)) {
            return false;
        }
        return location.toLowerCase().endsWith(".yml") || location.toLowerCase().endsWith(".yaml");
    }

    /**
     * Determines the base-path according to various configuration on component/endpoint and in the spec
     */
    public static String determineBasePath(
            CamelContext camelContext, RestOpenApiComponent component, RestOpenApiEndpoint endpoint, OpenAPI openAPI) {
        if (endpoint != null && isNotEmpty(endpoint.getBasePath())) {
            return endpoint.getBasePath();
        }

        if (component != null) {
            String componentBasePath = component.getBasePath();
            if (isNotEmpty(componentBasePath)) {
                return componentBasePath;
            }
        }

        String cn = endpoint != null ? endpoint.determineComponentName() : null;
        RestConfiguration restConfiguration = CamelContextHelper.getRestConfiguration(camelContext, null, cn);
        String restConfigurationBasePath = restConfiguration.getContextPath();

        if (isNotEmpty(restConfigurationBasePath)) {
            return restConfigurationBasePath;
        }

        // openapi spec should be last, as all the above can override the configuration
        if (openAPI != null) {
            String specificationBasePath = RestOpenApiHelper.getBasePathFromOpenApi(openAPI);
            if (isNotEmpty(specificationBasePath)) {
                return specificationBasePath;
            }
        }

        return RestOpenApiComponent.DEFAULT_BASE_PATH;
    }

    public static String getBasePathFromOpenApi(final OpenAPI openApi) {
        String basePath = null;
        if (openApi.getServers() != null) {
            for (Server server : openApi.getServers()) {
                if (server.getUrl() != null) {
                    try {
                        URI serverUrl = new URI(parseVariables(server.getUrl(), server));
                        basePath = serverUrl.getPath();
                        // Is this really necessary?
                        if (basePath.indexOf("//") == 0) {
                            // strip off the first "/" if double "/" exists
                            basePath = basePath.substring(1);
                        }
                        // strip ending slash
                        if (basePath.endsWith("/")) {
                            basePath = basePath.substring(0, basePath.length() - 1);
                        }
                        if ("/".equals(basePath)) {
                            basePath = "";
                        }
                    } catch (URISyntaxException e) {
                        // not a valid whole url, just the basePath
                        basePath = server.getUrl();
                    }
                }
            }
        }
        return basePath;
    }

    public static String parseVariables(String url, Server server) {
        Pattern p = Pattern.compile("\\{(.*?)\\}");
        Matcher m = p.matcher(url);
        while (m.find()) {

            String variable = m.group(1);
            if (server != null
                    && server.getVariables() != null
                    && server.getVariables().get(variable) != null) {
                String varValue = server.getVariables().get(variable).getDefault();
                url = url.replace("{" + variable + "}", varValue);
            }
        }
        return url;
    }
}
