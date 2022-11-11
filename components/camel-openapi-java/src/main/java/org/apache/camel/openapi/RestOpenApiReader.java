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
package org.apache.camel.openapi;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.core.models.ExtensibleNode;
import io.apicurio.datamodels.core.models.Extension;
import io.apicurio.datamodels.core.models.Node;
import io.apicurio.datamodels.core.models.common.AuthorizationCodeOAuthFlow;
import io.apicurio.datamodels.core.models.common.ClientCredentialsOAuthFlow;
import io.apicurio.datamodels.core.models.common.ImplicitOAuthFlow;
import io.apicurio.datamodels.core.models.common.OAuthFlow;
import io.apicurio.datamodels.core.models.common.PasswordOAuthFlow;
import io.apicurio.datamodels.core.models.common.SecurityRequirement;
import io.apicurio.datamodels.core.models.common.Tag;
import io.apicurio.datamodels.core.visitors.TraverserDirection;
import io.apicurio.datamodels.openapi.models.OasDocument;
import io.apicurio.datamodels.openapi.models.OasOperation;
import io.apicurio.datamodels.openapi.models.OasParameter;
import io.apicurio.datamodels.openapi.models.OasPathItem;
import io.apicurio.datamodels.openapi.models.OasSchema;
import io.apicurio.datamodels.openapi.models.OasSecurityRequirement;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v2.models.Oas20Header;
import io.apicurio.datamodels.openapi.v2.models.Oas20Items;
import io.apicurio.datamodels.openapi.v2.models.Oas20Operation;
import io.apicurio.datamodels.openapi.v2.models.Oas20Parameter;
import io.apicurio.datamodels.openapi.v2.models.Oas20Response;
import io.apicurio.datamodels.openapi.v2.models.Oas20Schema;
import io.apicurio.datamodels.openapi.v2.models.Oas20SchemaDefinition;
import io.apicurio.datamodels.openapi.v2.models.Oas20SecurityScheme;
import io.apicurio.datamodels.openapi.v2.visitors.Oas20AllNodeVisitor;
import io.apicurio.datamodels.openapi.v3.models.Oas30Document;
import io.apicurio.datamodels.openapi.v3.models.Oas30Header;
import io.apicurio.datamodels.openapi.v3.models.Oas30MediaType;
import io.apicurio.datamodels.openapi.v3.models.Oas30Operation;
import io.apicurio.datamodels.openapi.v3.models.Oas30Parameter;
import io.apicurio.datamodels.openapi.v3.models.Oas30Response;
import io.apicurio.datamodels.openapi.v3.models.Oas30Schema;
import io.apicurio.datamodels.openapi.v3.models.Oas30SchemaDefinition;
import io.apicurio.datamodels.openapi.v3.models.Oas30SecurityScheme;
import io.apicurio.datamodels.openapi.v3.visitors.Oas30AllNodeVisitor;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.ByteArraySchema;
import io.swagger.v3.oas.models.media.DateSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.PasswordSchema;
import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.model.rest.ApiKeyDefinition;
import org.apache.camel.model.rest.BasicAuthDefinition;
import org.apache.camel.model.rest.BearerTokenDefinition;
import org.apache.camel.model.rest.MutualTLSDefinition;
import org.apache.camel.model.rest.OAuth2Definition;
import org.apache.camel.model.rest.OpenIdConnectDefinition;
import org.apache.camel.model.rest.ParamDefinition;
import org.apache.camel.model.rest.ResponseHeaderDefinition;
import org.apache.camel.model.rest.ResponseMessageDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.model.rest.RestPropertyDefinition;
import org.apache.camel.model.rest.RestSecuritiesDefinition;
import org.apache.camel.model.rest.RestSecurityDefinition;
import org.apache.camel.model.rest.SecurityDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.FileUtil;

import static java.lang.invoke.MethodHandles.publicLookup;

/**
 * A Camel REST-DSL openApi reader that parse the rest-dsl into a openApi model representation.
 * <p/>
 * This reader supports the <a href="https://www.openapis.org/">OpenApi Specification 2.0 and 3.0</a>
 */
public class RestOpenApiReader {

    public static final String OAS20_SCHEMA_DEFINITION_PREFIX = "#/definitions/";
    public static final String OAS30_SCHEMA_DEFINITION_PREFIX = "#/components/schemas/";
    // Types that are not allowed in references.
    private static final Set<String> NO_REFERENCE_TYPE_NAMES = new HashSet<>(
            Arrays.asList(
                    "byte", "char", "short", "int", "java.lang.Integer", "long", "java.lang.Long", "float", "java.lang.Float",
                    "double", "java.lang.Double", "string", "java.lang.String", "boolean", "java.lang.Boolean",
                    "file", "java.io.File"));

    private static String getValue(CamelContext camelContext, String text) {
        return camelContext.resolvePropertyPlaceholders(text);
    }

    private static List<String> getValue(CamelContext camelContext, List<String> list) {
        if (list == null) {
            return null;
        }
        List<String> answer = new ArrayList<>();
        for (String line : list) {
            answer.add(camelContext.resolvePropertyPlaceholders(line));
        }
        return answer;
    }

    /**
     * Read the REST-DSL definition's and parse that as a OpenApi model representation
     *
     * @param  camelContext           the camel context
     * @param  rests                  the rest-dsl
     * @param  config                 the openApi configuration
     * @param  classResolver          class resolver to use @return the openApi model
     * @throws ClassNotFoundException is thrown if error loading class
     */
    public OasDocument read(
            CamelContext camelContext, List<RestDefinition> rests, BeanConfig config,
            String camelContextId, ClassResolver classResolver)
            throws ClassNotFoundException {

        OasDocument openApi;
        if (config.isOpenApi3()) {
            openApi = new Oas30Document();
        } else {
            openApi = new Oas20Document();
        }

        for (RestDefinition rest : rests) {
            parse(camelContext, openApi, rest, camelContextId, classResolver);
        }

        shortenClassNames(openApi);

        /*
         * Fixes the problem of not generating the "paths" section when no rest route is defined.
         * A schema with no paths is considered invalid.
         */
        if (openApi.paths == null) {
            openApi.paths = openApi.createPaths();
        }

        /*
         * Fixes the problem of generating duplicated tags which is invalid per the specification
         */
        if (openApi.tags != null) {
            openApi.tags = new ArrayList<>(
                    openApi.tags
                            .stream()
                            .collect(Collectors.toMap(Tag::getName, Function.identity(), (prev, current) -> prev))
                            .values());
        }

        // configure before returning
        openApi = config.configure(openApi);
        return openApi;
    }

    private void parse(
            CamelContext camelContext, OasDocument openApi, RestDefinition rest, String camelContextId,
            ClassResolver classResolver)
            throws ClassNotFoundException {

        List<VerbDefinition> verbs = new ArrayList<>(rest.getVerbs());
        // must sort the verbs by uri so we group them together when an uri has multiple operations
        verbs.sort(new VerbOrdering(camelContext));

        // we need to group the operations within the same tag, so use the path as default if not configured
        // Multi tag support for a comma delimeted tag
        String[] pathAsTags = null != rest.getTag()
                ? getValue(camelContext, rest.getTag()).split(",")
                : null != rest.getPath()
                        ? new String[] { getValue(camelContext, rest.getPath()) }
                : new String[0];

        if (openApi instanceof Oas20Document) {
            parseOas20(camelContext, (Oas20Document) openApi, rest, pathAsTags);
        } else if (openApi instanceof Oas30Document) {
            parseOas30((Oas30Document) openApi, rest, pathAsTags);
        }

        // gather all types in use
        Set<String> types = new LinkedHashSet<>();
        for (VerbDefinition verb : verbs) {

            // check if the Verb Definition must be excluded from documentation
            String apiDocs;
            if (verb.getApiDocs() != null) {
                apiDocs = verb.getApiDocs();
            } else {
                // fallback to option on rest
                apiDocs = rest.getApiDocs();
            }
            if (apiDocs != null && !Boolean.parseBoolean(apiDocs)) {
                continue;
            }

            String type = verb.getType();
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(type)) {
                if (type.endsWith("[]")) {
                    type = type.substring(0, type.length() - 2);
                }
                types.add(type);
            }
            type = verb.getOutType();
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(type)) {
                if (type.endsWith("[]")) {
                    type = type.substring(0, type.length() - 2);
                }
                types.add(type);
            }
            // there can also be types in response messages
            if (verb.getResponseMsgs() != null) {
                for (ResponseMessageDefinition def : verb.getResponseMsgs()) {
                    type = def.getResponseModel();
                    if (org.apache.camel.util.ObjectHelper.isNotEmpty(type)) {
                        if (type.endsWith("[]")) {
                            type = type.substring(0, type.length() - 2);
                        }
                        types.add(type);
                    }
                }
            }
        }

        // use annotation scanner to find models (annotated classes)
        for (String type : types) {
            Class<?> clazz = classResolver.resolveMandatoryClass(type);
            appendModels(clazz, openApi);
        }

        doParseVerbs(camelContext, openApi, rest, camelContextId, verbs, pathAsTags);

        // setup root security node if necessary
        List<SecurityDefinition> securityRequirements = rest.getSecurityRequirements();
        securityRequirements.forEach(requirement -> {
            OasSecurityRequirement oasRequirement = openApi.createSecurityRequirement();
            List<String> scopes;
            if (requirement.getScopes() == null || requirement.getScopes().trim().isEmpty()) {
                scopes = Collections.emptyList();
            } else {
                scopes = Arrays.asList(requirement.getScopes().trim().split("\\s*,\\s*"));
            }
            oasRequirement.addSecurityRequirementItem(requirement.getKey(), scopes);
            openApi.addSecurityRequirement(oasRequirement);
        });
    }

    private void parseOas30(Oas30Document openApi, RestDefinition rest, String[] pathAsTags) {
        String summary = rest.getDescriptionText();

        for (String tag : pathAsTags) {
            // add rest as tag
            openApi.addTag(tag, summary);
        }

        // setup security definitions
        RestSecuritiesDefinition sd = rest.getSecurityDefinitions();
        if (sd != null && !sd.getSecurityDefinitions().isEmpty() && openApi.components == null) {
            openApi.components = openApi
                    .createComponents();
        }
        if (sd != null) {
            for (RestSecurityDefinition def : sd.getSecurityDefinitions()) {
                if (def instanceof BasicAuthDefinition) {
                    Oas30SecurityScheme auth = openApi.components
                            .createSecurityScheme(def.getKey());
                    auth.type = "http";
                    auth.scheme = "basic";
                    auth.description = def.getDescription();
                    openApi.components.addSecurityScheme(def.getKey(), auth);
                } else if (def instanceof BearerTokenDefinition) {
                    Oas30SecurityScheme auth = openApi.components.createSecurityScheme(def.getKey());
                    auth.type = "http";
                    auth.scheme = "bearer";
                    auth.description = def.getDescription();
                    auth.bearerFormat = ((BearerTokenDefinition) def).getFormat();
                    openApi.components.addSecurityScheme(def.getKey(), auth);
                } else if (def instanceof ApiKeyDefinition) {
                    ApiKeyDefinition rs = (ApiKeyDefinition) def;
                    Oas30SecurityScheme auth = openApi.components
                            .createSecurityScheme(def.getKey());
                    auth.type = "apiKey";
                    auth.description = rs.getDescription();
                    auth.name = rs.getName();
                    if (rs.getInHeader() != null && Boolean.parseBoolean(rs.getInHeader())) {
                        auth.in = "header";
                    } else if (rs.getInQuery() != null && Boolean.parseBoolean(rs.getInQuery())) {
                        auth.in = "query";
                    } else if (rs.getInCookie() != null && Boolean.parseBoolean(rs.getInCookie())) {
                        auth.in = "cookie";
                    } else {
                        throw new IllegalStateException("No API Key location specified.");
                    }
                    openApi.components.addSecurityScheme(def.getKey(), auth);
                } else if (def instanceof OAuth2Definition) {
                    OAuth2Definition rs = (OAuth2Definition) def;

                    Oas30SecurityScheme auth = openApi.components
                            .createSecurityScheme(def.getKey());
                    auth.type = "oauth2";
                    auth.description = rs.getDescription();
                    String flow = rs.getFlow();
                    if (flow == null) {
                        flow = inferOauthFlow(rs);
                    }
                    OAuthFlow oauthFlow;
                    if (auth.flows == null) {
                        auth.flows = auth.createOAuthFlows();
                    }
                    switch (flow) {
                        case "authorizationCode":
                        case "accessCode":
                            AuthorizationCodeOAuthFlow authorizationCodeOAuthFlow
                                    = auth.flows.createAuthorizationCodeOAuthFlow();
                            oauthFlow = authorizationCodeOAuthFlow;
                            auth.flows.authorizationCode = authorizationCodeOAuthFlow;
                            break;
                        case "implicit":
                            ImplicitOAuthFlow implicitOAuthFlow = auth.flows.createImplicitOAuthFlow();
                            oauthFlow = implicitOAuthFlow;
                            auth.flows.implicit = implicitOAuthFlow;
                            break;
                        case "clientCredentials":
                        case "application":
                            ClientCredentialsOAuthFlow clientCredentialsOAuthFlow
                                    = auth.flows.createClientCredentialsOAuthFlow();
                            oauthFlow = clientCredentialsOAuthFlow;
                            auth.flows.clientCredentials = clientCredentialsOAuthFlow;
                            break;
                        case "password":
                            PasswordOAuthFlow passwordOAuthFlow = auth.flows.createPasswordOAuthFlow();
                            oauthFlow = passwordOAuthFlow;
                            auth.flows.password = passwordOAuthFlow;
                            break;
                        default:
                            throw new IllegalStateException("Invalid OAuth flow '" + flow + "' specified");
                    }
                    oauthFlow.authorizationUrl = rs.getAuthorizationUrl();
                    oauthFlow.tokenUrl = rs.getTokenUrl();
                    oauthFlow.refreshUrl = rs.getRefreshUrl();
                    for (RestPropertyDefinition scope : rs.getScopes()) {
                        oauthFlow.addScope(scope.getKey(), scope.getValue());
                    }

                    openApi.components.addSecurityScheme(def.getKey(), auth);
                } else if (def instanceof MutualTLSDefinition) {
                    Oas30SecurityScheme auth = openApi.components.createSecurityScheme(def.getKey());
                    auth.type = "mutualTLS";
                    openApi.components.addSecurityScheme(def.getKey(), auth);
                } else if (def instanceof OpenIdConnectDefinition) {
                    Oas30SecurityScheme auth = openApi.components.createSecurityScheme(def.getKey());
                    auth.type = "openIdConnect";
                    auth.openIdConnectUrl = ((OpenIdConnectDefinition) def).getUrl();
                    openApi.components.addSecurityScheme(def.getKey(), auth);
                }
            }
        }
    }

    private void parseOas20(CamelContext camelContext, Oas20Document openApi, RestDefinition rest, String[] pathAsTags) {
        String summary = getValue(camelContext, rest.getDescriptionText());

        for (String tag : pathAsTags) {
            // add rest as tag
            openApi.addTag(tag, summary);
        }

        // setup security definitions
        RestSecuritiesDefinition sd = rest.getSecurityDefinitions();
        if (sd != null && !sd.getSecurityDefinitions().isEmpty() && openApi.securityDefinitions == null) {
            openApi.securityDefinitions = openApi.createSecurityDefinitions();
        }
        if (sd != null) {
            for (RestSecurityDefinition def : sd.getSecurityDefinitions()) {
                if (def instanceof BasicAuthDefinition) {
                    Oas20SecurityScheme auth
                            = openApi.securityDefinitions.createSecurityScheme(getValue(camelContext, def.getKey()));
                    auth.type = "basicAuth";
                    auth.description = getValue(camelContext, def.getDescription());
                    openApi.securityDefinitions.addSecurityScheme(getValue(camelContext, def.getKey()), auth);
                } else if (def instanceof BearerTokenDefinition) {
                    throw new IllegalStateException("OpenAPI 2.0 does not support bearer token security schemes.");
                } else if (def instanceof ApiKeyDefinition) {
                    ApiKeyDefinition rs = (ApiKeyDefinition) def;
                    Oas20SecurityScheme auth
                            = openApi.securityDefinitions.createSecurityScheme(getValue(camelContext, def.getKey()));
                    auth.type = "apiKey";
                    auth.description = getValue(camelContext, rs.getDescription());
                    auth.name = getValue(camelContext, rs.getName());
                    if (rs.getInHeader() != null && CamelContextHelper.parseBoolean(camelContext, rs.getInHeader())) {
                        auth.in = "header";
                    } else if (rs.getInQuery() != null && CamelContextHelper.parseBoolean(camelContext, rs.getInQuery())) {
                        auth.in = "query";
                    } else {
                        throw new IllegalStateException("Invalid 'in' value for API Key security scheme");
                    }
                    openApi.securityDefinitions.addSecurityScheme(getValue(camelContext, def.getKey()), auth);
                } else if (def instanceof OAuth2Definition) {
                    OAuth2Definition rs = (OAuth2Definition) def;
                    Oas20SecurityScheme auth
                            = openApi.securityDefinitions.createSecurityScheme(getValue(camelContext, def.getKey()));
                    auth.type = "oauth2";
                    auth.description = getValue(camelContext, rs.getDescription());
                    String flow = rs.getFlow();
                    if (flow == null) {
                        flow = inferOauthFlow(rs);
                    }
                    switch (flow) {
                        case "accessCode":
                        case "authorizationCode":
                            auth.flow = "accessCode";
                            break;
                        case "application":
                        case "clientCredentials":
                            auth.flow = "application";
                            break;
                        case "password":
                        case "implicit":
                            auth.flow = flow;
                            break;
                        default:
                            throw new IllegalStateException("Invalid OAuth flow `" + flow + "'");
                    }
                    auth.authorizationUrl = getValue(camelContext, rs.getAuthorizationUrl());
                    auth.tokenUrl = getValue(camelContext, rs.getTokenUrl());
                    if (!rs.getScopes().isEmpty() && auth.scopes == null) {
                        auth.scopes = auth.createScopes();
                    }
                    for (RestPropertyDefinition scope : rs.getScopes()) {
                        auth.scopes.addScope(getValue(camelContext, scope.getKey()), getValue(camelContext, scope.getValue()));
                    }
                    if (openApi.securityDefinitions == null) {
                        openApi.securityDefinitions = openApi.createSecurityDefinitions();
                    }
                    openApi.securityDefinitions.addSecurityScheme(getValue(camelContext, def.getKey()), auth);
                } else if (def instanceof MutualTLSDefinition) {
                    throw new IllegalStateException("Mutual TLS security scheme is not supported");
                } else if (def instanceof OpenIdConnectDefinition) {
                    throw new IllegalStateException("OpenId Connect security scheme is not supported");
                }
            }
        }
    }

    private String buildBasePath(CamelContext camelContext, RestDefinition rest) {
        // used during gathering of apis
        String basePath = FileUtil.stripLeadingSeparator(getValue(camelContext, rest.getPath()));
        // must start with leading slash
        if (basePath != null && !basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }
        return basePath;
    }

    private void doParseVerbs(
            CamelContext camelContext, OasDocument openApi, RestDefinition rest, String camelContextId,
            List<VerbDefinition> verbs, String[] pathAsTags) {

        String basePath = buildBasePath(camelContext, rest);

        for (VerbDefinition verb : verbs) {
            // check if the Verb Definition must be excluded from documentation
            String apiDocs;
            if (verb.getApiDocs() != null) {
                apiDocs = getValue(camelContext, verb.getApiDocs());
            } else {
                // fallback to option on rest
                apiDocs = getValue(camelContext, rest.getApiDocs());
            }
            if (apiDocs != null && !Boolean.parseBoolean(apiDocs)) {
                continue;
            }

            // the method must be in lower case
            String method = verb.asVerb().toLowerCase(Locale.US);
            // operation path is a key
            String opPath = OpenApiHelper.buildUrl(basePath, getValue(camelContext, verb.getPath()));

            if (openApi.paths == null) {
                openApi.paths = openApi.createPaths();
            }
            OasPathItem path = openApi.paths.getPathItem(opPath);
            if (path == null) {
                path = openApi.paths.createPathItem(opPath);
            }

            OasOperation op = path.createOperation(method);
            for (String tag : pathAsTags) {
                // group in the same tag
                if (op.tags == null) {
                    op.tags = new ArrayList<>();
                }
                op.tags.add(tag);
            }

            // favour ids from verb, rest, route
            final String operationId;
            if (verb.getId() != null) {
                operationId = getValue(camelContext, verb.getId());
            } else if (rest.getId() != null) {
                operationId = getValue(camelContext, rest.getId());
            } else {
                verb.idOrCreate(camelContext.adapt(ExtendedCamelContext.class).getNodeIdFactory());
                operationId = verb.getId();
            }
            op.operationId = operationId;

            // add id as vendor extensions
            Extension extension = op.createExtension();
            extension.name = "x-camelContextId";
            extension.value = camelContextId;
            op.addExtension(extension.name, extension);
            extension = op.createExtension();
            op.addExtension(extension.name, extension);
            path = setPathOperation(path, op, method);

            String consumes = getValue(camelContext, verb.getConsumes() != null ? verb.getConsumes() : rest.getConsumes());
            String produces = getValue(camelContext, verb.getProduces() != null ? verb.getProduces() : rest.getProduces());
            if (openApi instanceof Oas20Document) {
                doParseVerbOas20(camelContext, (Oas20Document) openApi, verb, (Oas20Operation) op, consumes, produces);
            } else if (openApi instanceof Oas30Document) {
                doParseVerbOas30(camelContext, (Oas30Document) openApi, verb, (Oas30Operation) op, consumes, produces);
            }
            // enrich with configured response messages from the rest-dsl
            doParseResponseMessages(camelContext, openApi, verb, op, produces);

            // add path
            openApi.paths.addPathItem(opPath, path);
        }
    }

    private void doParseVerbOas30(
            CamelContext camelContext, Oas30Document openApi, VerbDefinition verb, Oas30Operation op, String consumes,
            String produces) {
        if (verb.getDescriptionText() != null) {
            op.summary = getValue(camelContext, verb.getDescriptionText());
        }

        if ("true".equals(verb.getDeprecated())) {
            op.deprecated = Boolean.TRUE;
        }

        // security
        for (SecurityDefinition sd : verb.getSecurity()) {
            List<String> scopes = new ArrayList<>();
            if (sd.getScopes() != null) {
                for (String scope : ObjectHelper.createIterable(getValue(camelContext, sd.getScopes()))) {
                    scopes.add(scope);
                }
            }
            SecurityRequirement securityRequirement = op.createSecurityRequirement();
            securityRequirement.addSecurityRequirementItem(getValue(camelContext, sd.getKey()), scopes);
            op.addSecurityRequirement(securityRequirement);
        }

        for (ParamDefinition param : verb.getParams()) {
            OasParameter parameter = null;
            if (param.getType().equals(RestParamType.body)) {
                parameter = op.createParameter();
                parameter.in = "body";
            } else if (param.getType().equals(RestParamType.formData)) {
                parameter = op.createParameter();
                parameter.in = "formData";
            } else if (param.getType().equals(RestParamType.header)) {
                parameter = op.createParameter();
                parameter.in = "header";
            } else if (param.getType().equals(RestParamType.path)) {
                parameter = op.createParameter();
                parameter.in = "path";
            } else if (param.getType().equals(RestParamType.query)) {
                parameter = op.createParameter();
                parameter.in = "query";
            }

            if (parameter != null) {
                parameter.name = getValue(camelContext, param.getName());
                if (org.apache.camel.util.ObjectHelper.isNotEmpty(param.getDescription())) {
                    parameter.description = getValue(camelContext, param.getDescription());
                }
                parameter.required = param.getRequired();

                // set type on parameter
                if (!parameter.in.equals("body")) {
                    Oas30Parameter parameter30 = (Oas30Parameter) parameter;
                    Oas30Schema oas30Schema = null;
                    final boolean isArray = getValue(camelContext, param.getDataType()).equalsIgnoreCase("array");
                    final List<String> allowableValues = getValue(camelContext, param.getAllowableValues());
                    final boolean hasAllowableValues = allowableValues != null && !allowableValues.isEmpty();
                    if (param.getDataType() != null) {
                        parameter30.schema = parameter30.createSchema();
                        oas30Schema = (Oas30Schema) parameter30.schema;
                        oas30Schema.type = getValue(camelContext, param.getDataType());
                        if (param.getDataFormat() != null) {
                            oas30Schema.format = getValue(camelContext, param.getDataFormat());
                        }
                        if (isArray) {
                            String arrayType = getValue(camelContext, param.getArrayType());
                            if (arrayType != null) {
                                if (arrayType.equalsIgnoreCase("string")) {
                                    defineSchemas(parameter30, allowableValues, String.class);
                                }
                                if (arrayType.equalsIgnoreCase("int") || arrayType.equalsIgnoreCase("integer")) {
                                    defineSchemas(parameter30, allowableValues, Integer.class);
                                }
                                if (arrayType.equalsIgnoreCase("long")) {
                                    defineSchemas(parameter30, allowableValues, Long.class);
                                }
                                if (arrayType.equalsIgnoreCase("float")) {
                                    defineSchemas(parameter30, allowableValues, Float.class);
                                }
                                if (arrayType.equalsIgnoreCase("double")) {
                                    defineSchemas(parameter30, allowableValues, Double.class);
                                }
                                if (arrayType.equalsIgnoreCase("boolean")) {
                                    defineSchemas(parameter30, allowableValues, Boolean.class);
                                }
                                if (arrayType.equalsIgnoreCase("byte")) {
                                    defineSchemas(parameter30, allowableValues, ByteArraySchema.class);
                                }
                                if (arrayType.equalsIgnoreCase("binary")) {
                                    defineSchemas(parameter30, allowableValues, BinarySchema.class);
                                }
                                if (arrayType.equalsIgnoreCase("date")) {
                                    defineSchemas(parameter30, allowableValues, DateSchema.class);
                                }
                                if (arrayType.equalsIgnoreCase("date-time")) {
                                    defineSchemas(parameter30, allowableValues, DateTimeSchema.class);
                                }
                                if (arrayType.equalsIgnoreCase("password")) {
                                    defineSchemas(parameter30, allowableValues, PasswordSchema.class);
                                }
                            }
                        }
                    }
                    if (param.getCollectionFormat() != null) {
                        parameter30.style = getValue(camelContext, param.getCollectionFormat().name());
                    }
                    if (hasAllowableValues && !isArray) {
                        oas30Schema.enum_ = allowableValues;
                    }

                    // set default value on parameter
                    if (org.apache.camel.util.ObjectHelper.isNotEmpty(param.getDefaultValue())) {
                        oas30Schema.default_ = getValue(camelContext, param.getDefaultValue());
                    }
                    // add examples
                    if (param.getExamples() != null && !param.getExamples().isEmpty()) {
                        // we can only set one example on the parameter
                        Extension exampleExtension = parameter30.createExtension();
                        boolean emptyKey = param.getExamples().get(0).getKey().length() == 0;
                        if (emptyKey) {
                            exampleExtension.name = "x-example";
                            exampleExtension.value = getValue(camelContext, param.getExamples().get(0).getValue());
                            parameter30.addExtension("x-example", exampleExtension);
                        } else {
                            Map<String, String> exampleValue = new LinkedHashMap<>();
                            exampleValue.put(getValue(camelContext, param.getExamples().get(0).getKey()),
                                    getValue(camelContext, param.getExamples().get(0).getValue()));
                            exampleExtension.name = "x-examples";
                            exampleExtension.value = exampleValue;
                            parameter30.addExtension("x-examples", exampleExtension);
                        }
                    }
                }

                // set schema on body parameter
                if (parameter.in.equals("body")) {
                    Oas30Parameter bp = (Oas30Parameter) parameter;
                    String type = getValue(camelContext, param.getDataType() != null ? param.getDataType() : verb.getType());
                    if (type != null) {
                        if (type.endsWith("[]")) {
                            type = type.substring(0, type.length() - 2);

                            OasSchema arrayModel = (Oas30Schema) bp.createSchema();
                            arrayModel = modelTypeAsProperty(type, openApi, arrayModel);
                            bp.schema = arrayModel;

                        } else {
                            String ref = modelTypeAsRef(type, openApi);
                            if (ref != null) {
                                Oas30Schema refModel = (Oas30Schema) bp.createSchema();
                                refModel.$ref = OAS30_SCHEMA_DEFINITION_PREFIX + ref;
                                bp.schema = refModel;
                            } else {
                                OasSchema model = (Oas30Schema) bp.createSchema();
                                model = modelTypeAsProperty(type, openApi, model);
                                bp.schema = model;
                            }
                        }
                    }

                    if (consumes != null) {
                        String[] parts = consumes.split(",");
                        if (op.requestBody == null) {
                            op.requestBody = op.createRequestBody();
                            op.requestBody.required = param.getRequired();
                            op.requestBody.description = getValue(camelContext, param.getDescription());
                        }
                        for (String part : parts) {
                            Oas30MediaType mediaType = op.requestBody.createMediaType(part);
                            mediaType.schema = mediaType.createSchema();
                            mediaType.schema.$ref = bp.schema.$ref;
                            op.requestBody.addMediaType(part, mediaType);
                        }
                    }

                    // add examples
                    if (param.getExamples() != null) {
                        Extension exampleExtension = op.requestBody.createExtension();
                        boolean emptyKey = param.getExamples().get(0).getKey().length() == 0;
                        if (emptyKey) {
                            exampleExtension.name = "x-example";
                            exampleExtension.value = getValue(camelContext, param.getExamples().get(0).getValue());
                            op.requestBody.addExtension("x-example", exampleExtension);
                        } else {
                            Map<String, String> exampleValue = new LinkedHashMap<>();
                            exampleValue.put(getValue(camelContext, param.getExamples().get(0).getKey()),
                                    getValue(camelContext, param.getExamples().get(0).getValue()));
                            exampleExtension.name = "x-examples";
                            exampleExtension.value = exampleValue;
                            op.requestBody.addExtension("x-examples", exampleExtension);
                        }
                    }
                    parameter = null;
                }

                op.addParameter(parameter);
            }
        }

        // clear parameters if its empty
        if (op.getParameters() != null && op.getParameters().isEmpty()) {
            op.parameters.clear();
        }

        // if we have an out type then set that as response message
        if (verb.getOutType() != null) {
            if (op.responses == null) {
                op.responses = op.createResponses();
            }
            Oas30Response response = (Oas30Response) op.responses.createResponse("200");
            String[] parts;
            if (produces != null) {
                parts = produces.split(",");
                for (String produce : parts) {
                    Oas30MediaType contentType = response.createMediaType(produce);
                    response.addMediaType(produce, contentType);
                    OasSchema model = contentType.createSchema();
                    model = modelTypeAsProperty(getValue(camelContext, verb.getOutType()), openApi, model);
                    contentType.schema = (Oas30Schema) model;
                    response.description = "Output type";
                    op.responses.addResponse("200", response);
                }
            }
        }

    }

    private void doParseVerbOas20(
            CamelContext camelContext, Oas20Document openApi, VerbDefinition verb, Oas20Operation op, String consumes,
            String produces) {
        if (consumes != null) {
            String[] parts = consumes.split(",");
            if (op.consumes == null) {
                op.consumes = new ArrayList<>();
            }
            op.consumes.addAll(Arrays.asList(parts));
        }

        if ("true".equals(verb.getDeprecated())) {
            op.deprecated = Boolean.TRUE;
        }

        if (produces != null) {
            String[] parts = produces.split(",");
            if (op.produces == null) {
                op.produces = new ArrayList<>();
            }
            op.produces.addAll(Arrays.asList(parts));
        }

        if (verb.getDescriptionText() != null) {
            op.summary = getValue(camelContext, verb.getDescriptionText());
        }

        // security
        for (SecurityDefinition sd : verb.getSecurity()) {
            List<String> scopes = new ArrayList<>();
            if (sd.getScopes() != null) {
                for (String scope : ObjectHelper.createIterable(getValue(camelContext, sd.getScopes()))) {
                    scopes.add(scope);
                }
            }
            SecurityRequirement securityRequirement = op.createSecurityRequirement();
            securityRequirement.addSecurityRequirementItem(getValue(camelContext, sd.getKey()), scopes);
            op.addSecurityRequirement(securityRequirement);
        }

        for (ParamDefinition param : verb.getParams()) {
            OasParameter parameter = null;
            if (param.getType().equals(RestParamType.body)) {
                parameter = op.createParameter();
                parameter.in = "body";
            } else if (param.getType().equals(RestParamType.formData)) {
                parameter = op.createParameter();
                parameter.in = "formData";
            } else if (param.getType().equals(RestParamType.header)) {
                parameter = op.createParameter();
                parameter.in = "header";
            } else if (param.getType().equals(RestParamType.path)) {
                parameter = op.createParameter();
                parameter.in = "path";
            } else if (param.getType().equals(RestParamType.query)) {
                parameter = op.createParameter();
                parameter.in = "query";
            }

            if (parameter != null) {
                parameter.name = getValue(camelContext, param.getName());
                if (org.apache.camel.util.ObjectHelper.isNotEmpty(param.getDescription())) {
                    parameter.description = getValue(camelContext, param.getDescription());
                }
                parameter.required = param.getRequired();

                // set type on parameter
                if (!parameter.in.equals("body")) {

                    Oas20Parameter serializableParameter = (Oas20Parameter) parameter;
                    final boolean isArray = getValue(camelContext, param.getDataType()).equalsIgnoreCase("array");
                    final List<String> allowableValues = getValue(camelContext, param.getAllowableValues());
                    final boolean hasAllowableValues = allowableValues != null && !allowableValues.isEmpty();
                    if (param.getDataType() != null) {
                        serializableParameter.type = param.getDataType();
                        if (param.getDataFormat() != null) {
                            serializableParameter.format = getValue(camelContext, param.getDataFormat());
                        }
                        if (isArray) {
                            if (param.getArrayType() != null) {
                                String arrayType = getValue(camelContext, param.getArrayType());
                                if (arrayType.equalsIgnoreCase("string")) {
                                    defineItems(serializableParameter, allowableValues, new Oas20Items(), String.class);
                                }
                                if (arrayType.equalsIgnoreCase("int") || arrayType.equalsIgnoreCase("integer")) {
                                    defineItems(serializableParameter, allowableValues, new Oas20Items(), Integer.class);
                                }
                                if (arrayType.equalsIgnoreCase("long")) {
                                    defineItems(serializableParameter, allowableValues, new Oas20Items(), Long.class);
                                }
                                if (arrayType.equalsIgnoreCase("float")) {
                                    defineItems(serializableParameter, allowableValues, new Oas20Items(), Float.class);
                                }
                                if (arrayType.equalsIgnoreCase("double")) {
                                    defineItems(serializableParameter, allowableValues, new Oas20Items(), Double.class);
                                }
                                if (arrayType.equalsIgnoreCase("boolean")) {
                                    defineItems(serializableParameter, allowableValues, new Oas20Items(), Boolean.class);
                                }
                            }
                        }
                    }
                    if (param.getCollectionFormat() != null) {
                        serializableParameter.collectionFormat = param.getCollectionFormat().name();
                    }
                    if (hasAllowableValues && !isArray) {
                        serializableParameter.enum_ = allowableValues;
                    }
                    // set default value on parameter
                    if (org.apache.camel.util.ObjectHelper.isNotEmpty(param.getDefaultValue())) {
                        serializableParameter.default_ = getValue(camelContext, param.getDefaultValue());
                    }
                    // add examples
                    if (param.getExamples() != null && !param.getExamples().isEmpty()) {
                        // we can only set one example on the parameter
                        Extension exampleExtension = serializableParameter.createExtension();
                        boolean emptyKey = param.getExamples().get(0).getKey().length() == 0;
                        if (emptyKey) {
                            exampleExtension.name = "x-example";
                            exampleExtension.value = getValue(camelContext, param.getExamples().get(0).getValue());
                            serializableParameter.addExtension("x-example", exampleExtension);
                        } else {
                            Map<String, String> exampleValue = new LinkedHashMap<>();
                            exampleValue.put(getValue(camelContext, param.getExamples().get(0).getKey()),
                                    getValue(camelContext, param.getExamples().get(0).getValue()));
                            exampleExtension.name = "x-examples";
                            exampleExtension.value = exampleValue;
                            serializableParameter.addExtension("x-examples", exampleExtension);
                        }
                    }
                }

                // set schema on body parameter
                if (parameter.in.equals("body")) {
                    Oas20Parameter bp = (Oas20Parameter) parameter;
                    String type = getValue(camelContext, param.getDataType() != null ? param.getDataType() : verb.getType());
                    if (type != null) {
                        if (type.endsWith("[]")) {
                            type = type.substring(0, type.length() - 2);
                            OasSchema arrayModel = (Oas20Schema) bp.createSchema();
                            arrayModel = modelTypeAsProperty(type, openApi, arrayModel);
                            bp.schema = arrayModel;
                        } else {
                            String ref = modelTypeAsRef(type, openApi);
                            if (ref != null) {
                                Oas20Schema refModel = (Oas20Schema) bp.createSchema();
                                refModel.$ref = OAS20_SCHEMA_DEFINITION_PREFIX + ref;
                                bp.schema = refModel;
                            } else {
                                OasSchema model = (Oas20Schema) bp.createSchema();
                                model = modelTypeAsProperty(type, openApi, model);
                                bp.schema = model;
                            }
                        }
                    }

                    // add examples
                    if (param.getExamples() != null) {
                        Extension exampleExtension = bp.createExtension();
                        boolean emptyKey = param.getExamples().get(0).getKey().length() == 0;
                        if (emptyKey) {
                            exampleExtension.name = "x-example";
                            exampleExtension.value = getValue(camelContext, param.getExamples().get(0).getValue());
                            bp.addExtension("x-example", exampleExtension);
                        } else {
                            Map<String, String> exampleValue = new LinkedHashMap<>();
                            exampleValue.put(getValue(camelContext, param.getExamples().get(0).getKey()),
                                    getValue(camelContext, param.getExamples().get(0).getValue()));
                            exampleExtension.name = "x-examples";
                            exampleExtension.value = exampleValue;
                            bp.addExtension("x-examples", exampleExtension);
                        }
                    }
                }
                op.addParameter(parameter);
            }
        }

        // clear parameters if its empty
        if (op.getParameters() != null && op.getParameters().isEmpty()) {
            op.parameters.clear();
        }

        // if we have an out type then set that as response message
        if (verb.getOutType() != null) {
            if (op.responses == null) {
                op.responses = op.createResponses();
            }
            Oas20Response response = (Oas20Response) op.responses.createResponse("200");
            OasSchema model = response.createSchema();
            model = modelTypeAsProperty(getValue(camelContext, verb.getOutType()), openApi, model);

            response.schema = (Oas20Schema) model;
            response.description = "Output type";
            op.responses.addResponse("200", response);
        }
    }

    private OasPathItem setPathOperation(OasPathItem path, OasOperation operation, String method) {
        if (method.equals("post")) {
            path.post = operation;
        } else if (method.equals("get")) {
            path.get = operation;
        } else if (method.equals("put")) {
            path.put = operation;
        } else if (method.equals("patch")) {
            path.patch = operation;
        } else if (method.equals("delete")) {
            path.delete = operation;
        } else if (method.equals("head")) {
            path.head = operation;
        } else if (method.equals("options")) {
            path.options = operation;
        }
        return path;
    }

    private static void defineItems(
            final Oas20Parameter serializableParameter,
            final List<String> allowableValues, final Oas20Items items,
            final Class<?> type) {
        serializableParameter.items = items;
        if (allowableValues != null && !allowableValues.isEmpty()) {
            if (String.class.equals(type)) {
                items.enum_ = allowableValues;
            } else {
                convertAndSetItemsEnum(items, allowableValues, type);
            }
        } else if (Objects.equals(serializableParameter.type, "array")) {
            Oas20Items oas20Items = serializableParameter.createItems();
            oas20Items.type = type.getSimpleName().toLowerCase();
            serializableParameter.items = oas20Items;
        }
    }

    private static void defineSchemas(
            final Oas30Parameter serializableParameter,
            final List<String> allowableValues,
            final Class<?> type) {
        if (allowableValues != null && !allowableValues.isEmpty()) {
            if (String.class.equals(type)) {
                ((Oas30Schema) serializableParameter.schema).enum_ = allowableValues;
            } else {
                convertAndSetItemsEnum(serializableParameter.schema, allowableValues, type);
            }
        } else if (Objects.equals(((Oas30Schema) serializableParameter.schema).type, "array")) {
            Oas30Schema parameterSchema = (Oas30Schema) serializableParameter.schema;
            OasSchema itemsSchema = parameterSchema.createItemsSchema();

            if (Integer.class.equals(type)) {
                itemsSchema.type = "number";
                itemsSchema.format = "int32";
            } else if (Long.class.equals(type)) {
                itemsSchema.type = "number";
                itemsSchema.format = "int64";
            } else if (Float.class.equals(type)) {
                itemsSchema.type = "number";
                itemsSchema.format = "float";
            } else if (Double.class.equals(type)) {
                itemsSchema.type = "number";
                itemsSchema.format = "double";
            } else if (ByteArraySchema.class.equals(type)) {
                itemsSchema.type = "string";
                itemsSchema.format = "byte";
            } else if (BinarySchema.class.equals(type)) {
                itemsSchema.type = "string";
                itemsSchema.format = "binary";
            } else if (Date.class.equals(type)) {
                itemsSchema.type = "string";
                itemsSchema.format = "date";
            } else if (DateTimeSchema.class.equals(type)) {
                itemsSchema.type = "string";
                itemsSchema.format = "date-time";
            } else if (PasswordSchema.class.equals(type)) {
                itemsSchema.type = "string";
                itemsSchema.format = "password";
            } else {
                itemsSchema.type = "string";
            }

            parameterSchema.items = itemsSchema;
        }
    }

    private static void convertAndSetItemsEnum(
            final ExtensibleNode items, final List<String> allowableValues,
            final Class<?> type) {
        try {
            final MethodHandle valueOf = publicLookup().findStatic(type, "valueOf",
                    MethodType.methodType(type, String.class));
            final MethodHandle setEnum = publicLookup().bind(items, "setEnum",
                    MethodType.methodType(void.class, List.class));
            final List<?> values = allowableValues.stream().map(v -> {
                try {
                    return valueOf.invoke(v);
                } catch (Throwable e) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }
                    throw new IllegalStateException(e);
                }
            }).collect(Collectors.toList());
            setEnum.invoke(values);
        } catch (Throwable e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new IllegalStateException(e);
        }
    }

    private void doParseResponseMessages(
            CamelContext camelContext, OasDocument openApi, VerbDefinition verb, OasOperation op, String produces) {
        if (op.responses == null) {
            op.responses = op.createResponses();
        }
        for (ResponseMessageDefinition msg : verb.getResponseMsgs()) {
            if (openApi instanceof Oas20Document) {
                doParseResponseOas20(camelContext, (Oas20Document) openApi, (Oas20Operation) op, msg);
            } else if (openApi instanceof Oas30Document) {
                doParseResponseOas30(camelContext, (Oas30Document) openApi, (Oas30Operation) op, produces, msg);
            }
        }

        // must include an empty noop response if none exists
        if (op.responses == null || op.responses.getResponses().isEmpty()) {
            op.responses.addResponse("200", op.responses.createResponse("200"));
        }
    }

    private void doParseResponseOas30(
            CamelContext camelContext, Oas30Document openApi, Oas30Operation op, String produces,
            ResponseMessageDefinition msg) {
        Oas30Response response = null;

        String code = getValue(camelContext, msg.getCode());
        if (op.responses != null && op.responses.getResponses() != null) {
            response = (Oas30Response) op.responses.getResponse(code);
        }
        if (response == null) {
            response = (Oas30Response) op.responses.createResponse(code);
            op.responses.addResponse(code, response);
        }
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(msg.getResponseModel())) {
            String[] parts;
            if (produces != null) {
                parts = produces.split(",");
                for (String produce : parts) {
                    Oas30MediaType contentType = response.createMediaType(produce);
                    response.addMediaType(produce, contentType);
                    OasSchema model = contentType.createSchema();
                    model = modelTypeAsProperty(getValue(camelContext, msg.getResponseModel()), openApi, model);
                    contentType.schema = (Oas30Schema) model;
                }
            }
        }
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(msg.getMessage())) {
            response.description = getValue(camelContext, msg.getMessage());
        }

        // add headers
        if (msg.getHeaders() != null) {
            for (ResponseHeaderDefinition header : msg.getHeaders()) {
                String name = getValue(camelContext, header.getName());
                String type = getValue(camelContext, header.getDataType());
                String format = getValue(camelContext, header.getDataFormat());

                if ("string".equals(type) || "long".equals(type) || "float".equals(type)
                        || "double".equals(type) || "boolean".equals(type)) {
                    setResponseHeaderOas30(camelContext, response, header, name, format, type);
                } else if ("int".equals(type) || "integer".equals(type)) {
                    setResponseHeaderOas30(camelContext, response, header, name, format, "integer");
                } else if ("array".equals(type)) {
                    Oas30Header ap = response.createHeader(name);

                    if (org.apache.camel.util.ObjectHelper.isNotEmpty(header.getDescription())) {
                        ap.description = getValue(camelContext, header.getDescription());
                    }
                    if (header.getArrayType() != null) {
                        String arrayType = getValue(camelContext, header.getArrayType());
                        if (arrayType.equalsIgnoreCase("string")
                                || arrayType.equalsIgnoreCase("long")
                                || arrayType.equalsIgnoreCase("float")
                                || arrayType.equalsIgnoreCase("double")
                                || arrayType.equalsIgnoreCase("boolean")) {
                            setHeaderSchemaOas30(ap, arrayType);
                        } else if (arrayType.equalsIgnoreCase("int")
                                || arrayType.equalsIgnoreCase("integer")) {
                            setHeaderSchemaOas30(ap, "integer");
                        }

                    }
                    // add example
                    if (header.getExample() != null) {
                        Extension exampleExtension = ap.createExtension();
                        exampleExtension.name = "x-example";
                        exampleExtension.value = getValue(camelContext, header.getExample());
                        ap.getExtensions().add(exampleExtension);
                    }
                    response.addHeader(name, ap);
                }
            }
        }

        // add examples
        if (msg.getExamples() != null) {
            Extension exampleExtension = response.createExtension();
            exampleExtension.name = "x-examples";
            Map<String, String> examplesValue = new LinkedHashMap<>();
            for (RestPropertyDefinition prop : msg.getExamples()) {
                examplesValue.put(getValue(camelContext, prop.getKey()), getValue(camelContext, prop.getValue()));

            }
            exampleExtension.value = examplesValue;
            response.addExtension(exampleExtension.name, exampleExtension);
        }
    }

    private void setHeaderSchemaOas30(Oas30Header ap, String arrayType) {
        Oas30Schema items = ap.createSchema();
        items.type = arrayType;
        ap.schema = items;
    }

    private void setResponseHeaderOas30(
            CamelContext camelContext, Oas30Response response, ResponseHeaderDefinition header,
            String name, String format, String type) {
        Oas30Header ip = response.createHeader(name);
        response.addHeader(name, ip);
        Oas30Schema schema = ip.createSchema();
        ip.schema = schema;
        schema.type = type;
        if (format != null) {
            schema.format = format;
        }
        ip.description = getValue(camelContext, header.getDescription());

        List<String> values;
        if (!header.getAllowableValues().isEmpty()) {
            values = new ArrayList<>();
            for (String text : header.getAllowableValues()) {
                values.add(getValue(camelContext, text));
            }
            schema.enum_ = values;
        }
        // add example
        if (header.getExample() != null) {
            Extension exampleExtension = ip.createExtension();
            exampleExtension.name = "x-example";
            exampleExtension.value = getValue(camelContext, header.getExample());
            ip.getExtensions().add(exampleExtension);
        }
    }

    private void doParseResponseOas20(
            CamelContext camelContext, Oas20Document openApi, Oas20Operation op,
            ResponseMessageDefinition msg) {
        Oas20Response response = null;

        String code = getValue(camelContext, msg.getCode());
        if (op.responses != null && op.responses.getResponses() != null) {
            response = (Oas20Response) op.responses.getResponse(code);
        }
        if (response == null) {
            response = (Oas20Response) op.responses.createResponse(code);
            op.responses.addResponse(code, response);
        }
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(msg.getResponseModel())) {
            OasSchema model = response.createSchema();
            model = modelTypeAsProperty(getValue(camelContext, msg.getResponseModel()), openApi, model);
            response.schema = (Oas20Schema) model;
        }
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(msg.getMessage())) {
            response.description = getValue(camelContext, msg.getMessage());
        }

        // add headers
        if (msg.getHeaders() != null) {
            for (ResponseHeaderDefinition header : msg.getHeaders()) {
                String name = getValue(camelContext, header.getName());
                String type = getValue(camelContext, header.getDataType());
                String format = getValue(camelContext, header.getDataFormat());
                if (response.headers == null) {
                    response.headers = response.createHeaders();
                }
                if ("string".equals(type) || "long".equals(type) || "float".equals(type)
                        || "double".equals(type) || "boolean".equals(type)) {
                    setResponseHeaderOas20(camelContext, response, header, name, format, type);
                } else if ("int".equals(type) || "integer".equals(type)) {
                    setResponseHeaderOas20(camelContext, response, header, name, format, "integer");
                } else if ("array".equals(type)) {
                    Oas20Header ap = response.headers.createHeader(name);

                    if (org.apache.camel.util.ObjectHelper.isNotEmpty(header.getDescription())) {
                        ap.description = getValue(camelContext, header.getDescription());
                    }
                    if (header.getArrayType() != null) {
                        String arrayType = header.getArrayType();
                        if (arrayType.equalsIgnoreCase("string")
                                || arrayType.equalsIgnoreCase("long")
                                || arrayType.equalsIgnoreCase("float")
                                || arrayType.equalsIgnoreCase("double")
                                || arrayType.equalsIgnoreCase("boolean")) {
                            setHeaderSchemaOas20(ap, arrayType);
                        } else if (header.getArrayType().equalsIgnoreCase("int")
                                || header.getArrayType().equalsIgnoreCase("integer")) {
                            setHeaderSchemaOas20(ap, "integer");
                        }

                    }
                    // add example
                    if (header.getExample() != null) {
                        Extension exampleExtension = ap.createExtension();
                        exampleExtension.name = "x-example";
                        exampleExtension.value = getValue(camelContext, header.getExample());
                        ap.getExtensions().add(exampleExtension);
                    }
                    response.headers.addHeader(name, ap);
                }
            }
        }

        // add examples
        if (msg.getExamples() != null) {
            Extension exampleExtension = response.createExtension();
            exampleExtension.name = "examples";
            Map<String, String> examplesValue = new LinkedHashMap<>();
            for (RestPropertyDefinition prop : msg.getExamples()) {
                examplesValue.put(getValue(camelContext, prop.getKey()), getValue(camelContext, prop.getValue()));

            }
            exampleExtension.value = examplesValue;
            response.addExtension(exampleExtension.name, exampleExtension);
        }
    }

    private void setHeaderSchemaOas20(Oas20Header ap, String arrayType) {
        Oas20Items items = ap.createItems();
        items.type = arrayType;
        ap.items = items;
    }

    private void setResponseHeaderOas20(
            CamelContext camelContext, Oas20Response response, ResponseHeaderDefinition header,
            String name, String format, String type) {
        Oas20Header ip = response.headers.createHeader(name);
        ip.type = type;
        if (format != null) {
            ip.format = format;
        }
        ip.description = getValue(camelContext, header.getDescription());

        List<String> values;
        if (!header.getAllowableValues().isEmpty()) {
            values = new ArrayList<>();
            for (String text : header.getAllowableValues()) {
                values.add(getValue(camelContext, text));
            }
            ip.enum_ = values;
        }
        // add example
        if (header.getExample() != null) {
            Extension exampleExtension = ip.createExtension();
            exampleExtension.name = "x-example";
            exampleExtension.value = getValue(camelContext, header.getExample());
            ip.getExtensions().add(exampleExtension);
        }
        response.headers.addHeader(name, ip);
    }

    private String modelTypeAsRef(String typeName, OasDocument openApi) {
        boolean array = typeName.endsWith("[]");
        if (array) {
            typeName = typeName.substring(0, typeName.length() - 2);
        }

        if (NO_REFERENCE_TYPE_NAMES.contains(typeName)) {
            return null;
        }

        if (openApi instanceof Oas20Document) {
            if (((Oas20Document) openApi).definitions != null) {
                for (Oas20SchemaDefinition model : ((Oas20Document) openApi).definitions.getDefinitions()) {
                    @SuppressWarnings("rawtypes")
                    Map modelType = (Map) model.getExtension("x-className").value;
                    if (modelType != null && typeName.equals(modelType.get("format"))) {
                        return model.getName();
                    }
                }
            }
        } else if (openApi instanceof Oas30Document) {
            if (((Oas30Document) openApi).components != null
                    && ((Oas30Document) openApi).components.schemas != null) {
                for (Oas30SchemaDefinition model : ((Oas30Document) openApi).components.schemas.values()) {
                    @SuppressWarnings("rawtypes")
                    Map modelType = (Map) model.getExtension("x-className").value;
                    if (modelType != null && typeName.equals(modelType.get("format"))) {
                        return model.getName();
                    }
                }
            }
        }
        return null;
    }

    private OasSchema modelTypeAsProperty(String typeName, OasDocument openApi, OasSchema prop) {
        boolean array = typeName.endsWith("[]");
        if (array) {
            typeName = typeName.substring(0, typeName.length() - 2);
        }

        String ref = modelTypeAsRef(typeName, openApi);

        if (ref != null) {
            if (openApi instanceof Oas20Document) {
                prop.$ref = OAS20_SCHEMA_DEFINITION_PREFIX + ref;
            } else if (openApi instanceof Oas30Document) {
                prop.$ref = OAS30_SCHEMA_DEFINITION_PREFIX + ref;
            }
        } else {
            // special for byte arrays
            if (array && ("byte".equals(typeName) || "java.lang.Byte".equals(typeName))) {
                prop.format = "byte";
                prop.type = "number";
                array = false;
            } else if ("string".equalsIgnoreCase(typeName) || "java.lang.String".equals(typeName)) {
                prop.format = "string";
                prop.type = "string";
            } else if ("int".equals(typeName) || "java.lang.Integer".equals(typeName)) {
                prop.format = "integer";
                prop.type = "number";
            } else if ("long".equals(typeName) || "java.lang.Long".equals(typeName)) {
                prop.format = "long";
                prop.type = "number";
            } else if ("float".equals(typeName) || "java.lang.Float".equals(typeName)) {
                prop.format = "float";
                prop.type = "number";
            } else if ("double".equals(typeName) || "java.lang.Double".equals(typeName)) {
                prop.format = "double";
                prop.type = "number";
            } else if ("boolean".equals(typeName) || "java.lang.Boolean".equals(typeName)) {
                prop.format = "boolean";
                prop.type = "number";
            } else if ("file".equals(typeName) || "java.io.File".equals(typeName)) {
                if (openApi instanceof Oas20Document) {
                    prop.type = "file";
                } else if (openApi instanceof Oas30Document) {
                    prop.type = "string";
                    prop.format = "binary";
                }
            } else {
                prop.type = "string";
            }
        }

        if (array) {
            OasSchema ret = prop.createItemsSchema();
            ret.$ref = prop.$ref;
            prop.$ref = null;
            prop.items = ret;
            prop.type = "array";
            return prop;
        } else {
            return prop;
        }
    }

    /**
     * If the class is annotated with openApi annotations its parsed into a OpenApi model representation which is added
     * to openApi
     *
     * @param clazz   the class such as pojo with openApi annotation
     * @param openApi the openApi model
     */
    private void appendModels(Class<?> clazz, OasDocument openApi) {
        RestModelConverters converters = new RestModelConverters();
        List<? extends OasSchema> models = converters.readClass(openApi, clazz);
        if (models == null) {
            return;
        }
        for (OasSchema entry : models) {
            // favor keeping any existing model that has the vendor extension in the model
            if (openApi instanceof Oas20Document) {
                boolean oldExt = false;
                if (((Oas20Document) openApi).definitions != null && ((Oas20Document) openApi).definitions
                        .getDefinition(((Oas20SchemaDefinition) entry).getName()) != null) {
                    Oas20SchemaDefinition oldModel = ((Oas20Document) openApi).definitions
                            .getDefinition(((Oas20SchemaDefinition) entry).getName());
                    if (oldModel.getExtensions() != null && !oldModel.getExtensions().isEmpty()) {
                        oldExt = oldModel.getExtension("x-className") != null;
                    }
                }

                if (!oldExt) {
                    ((Oas20Document) openApi).definitions
                            .addDefinition(((Oas20SchemaDefinition) entry).getName(),
                                    (Oas20SchemaDefinition) entry);
                }
            } else if (openApi instanceof Oas30Document) {
                boolean oldExt = false;
                if (((Oas30Document) openApi).components != null && ((Oas30Document) openApi).components
                        .getSchemaDefinition(((Oas30SchemaDefinition) entry).getName()) != null) {
                    Oas30SchemaDefinition oldModel = ((Oas30Document) openApi).components
                            .getSchemaDefinition(((Oas30SchemaDefinition) entry).getName());
                    if (oldModel.getExtensions() != null && !oldModel.getExtensions().isEmpty()) {
                        oldExt = oldModel.getExtension("x-className") != null;
                    }
                }

                if (!oldExt) {
                    ((Oas30Document) openApi).components
                            .addSchemaDefinition(((Oas30SchemaDefinition) entry).getName(),
                                    (Oas30SchemaDefinition) entry);
                }
            }
        }

    }

    /**
     * To sort the rest operations
     */
    private static class VerbOrdering implements Comparator<VerbDefinition> {

        private final CamelContext camelContext;

        public VerbOrdering(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        @Override
        public int compare(VerbDefinition a, VerbDefinition b) {
            String u1 = "";
            if (a.getPath() != null) {
                // replace { with _ which comes before a when soring by char
                u1 = getValue(camelContext, a.getPath()).replace("{", "_");
            }
            String u2 = "";
            if (b.getPath() != null) {
                // replace { with _ which comes before a when soring by char
                u2 = getValue(camelContext, b.getPath()).replace("{", "_");
            }

            int num = u1.compareTo(u2);
            if (num == 0) {
                // same uri, so use http method as sorting
                num = a.asVerb().compareTo(b.asVerb());
            }
            return num;
        }
    }

    private void shortenClassNames(OasDocument document) {
        if (document instanceof Oas30Document) {
            Oas30Document oas30Document = (Oas30Document) document;
            if (oas30Document.components == null || oas30Document.components.schemas == null) {
                return;
            }
        } else {
            Oas20Document oas20Document = (Oas20Document) document;
            if (oas20Document.definitions == null || oas20Document.definitions.getDefinitions() == null) {
                return;
            }
        }

        // Make a mapping from full name to possibly shortened name.
        Map<String, String> names = new HashMap<>();
        Stream<String> schemaStream;
        if (document instanceof Oas30Document) {
            schemaStream = ((Oas30Document) document).components.schemas.keySet().stream();
        } else {
            schemaStream = ((Oas20Document) document).definitions.getDefinitions().stream()
                    .map(Oas20SchemaDefinition::getName);
        }
        schemaStream.forEach(key -> {
            String s = key.replaceAll("[^a-zA-Z0-9.-_]", "_");
            String shortName = s.substring(s.lastIndexOf('.') + 1);
            names.put(key, names.containsValue(shortName) ? s : shortName);
        });

        if (document instanceof Oas30Document) {
            Library.visitTree(document, new Oas30AllNodeVisitor() {
                @Override
                protected void visitNode(Node node) {
                    if (node instanceof Oas30SchemaDefinition) {
                        Oas30SchemaDefinition definition = (Oas30SchemaDefinition) node;
                        definition.rename(fixSchemaReference(definition.getName(), names, OAS30_SCHEMA_DEFINITION_PREFIX));
                    } else if (node instanceof Oas30Schema) {
                        Oas30Schema schema = (Oas30Schema) node;
                        String ref = schema.$ref;
                        if (ref != null) {
                            schema.$ref = OAS30_SCHEMA_DEFINITION_PREFIX +
                                          fixSchemaReference(ref, names, OAS30_SCHEMA_DEFINITION_PREFIX);
                        }
                    }
                }
            }, TraverserDirection.down);
        } else {
            Library.visitTree(document, new Oas20AllNodeVisitor() {
                @Override
                protected void visitNode(Node node) {
                    if (node instanceof Oas20SchemaDefinition) {
                        Oas20SchemaDefinition definition = (Oas20SchemaDefinition) node;
                        definition.rename(fixSchemaReference(definition.getName(), names, OAS20_SCHEMA_DEFINITION_PREFIX));
                    } else if (node instanceof Oas20Schema) {
                        Oas20Schema schema = (Oas20Schema) node;
                        String ref = schema.$ref;
                        if (ref != null) {
                            schema.$ref = OAS20_SCHEMA_DEFINITION_PREFIX +
                                          fixSchemaReference(ref, names, OAS20_SCHEMA_DEFINITION_PREFIX);
                        }
                    }
                }
            }, TraverserDirection.down);
        }
    }

    private String fixSchemaReference(String ref, Map<String, String> names, String prefix) {
        if (ref.startsWith(prefix)) {
            ref = ref.substring(prefix.length());
        }

        String name = names.get(ref);
        return name == null ? ref : name;
    }

    private String inferOauthFlow(OAuth2Definition rs) {
        String flow;
        if (rs.getAuthorizationUrl() != null && rs.getTokenUrl() != null) {
            flow = "authorizationCode";
        } else if (rs.getTokenUrl() == null && rs.getAuthorizationUrl() != null) {
            flow = "implicit";
        } else {
            throw new IllegalStateException("Error inferring OAuth flow");
        }
        return flow;
    }

}
