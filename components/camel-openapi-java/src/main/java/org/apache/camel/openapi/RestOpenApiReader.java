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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.swagger.v3.core.filter.AbstractSpecFilter;
import io.swagger.v3.core.filter.SpecFilter;
import io.swagger.v3.core.model.ApiDescription;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.ByteArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.FileSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.PasswordSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.Parameter.StyleEnum;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.apache.camel.CamelContext;
import org.apache.camel.model.rest.ApiKeyDefinition;
import org.apache.camel.model.rest.BasicAuthDefinition;
import org.apache.camel.model.rest.BearerTokenDefinition;
import org.apache.camel.model.rest.CollectionFormat;
import org.apache.camel.model.rest.MutualTLSDefinition;
import org.apache.camel.model.rest.OAuth2Definition;
import org.apache.camel.model.rest.OpenIdConnectDefinition;
import org.apache.camel.model.rest.ParamDefinition;
import org.apache.camel.model.rest.ResponseHeaderDefinition;
import org.apache.camel.model.rest.ResponseMessageDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestPropertyDefinition;
import org.apache.camel.model.rest.RestSecuritiesDefinition;
import org.apache.camel.model.rest.RestSecurityDefinition;
import org.apache.camel.model.rest.SecurityDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.invoke.MethodHandles.publicLookup;

/**
 * A Camel REST-DSL openApi reader that parse the rest-dsl into a openApi model representation.
 * <p/>
 * This reader supports the <a href="https://www.openapis.org/">OpenApi Specification 2.0 and 3.0</a>
 */
public class RestOpenApiReader {

    public static final String OAS20_SCHEMA_DEFINITION_PREFIX = "#/definitions/";
    public static final String OAS30_SCHEMA_DEFINITION_PREFIX = "#/components/schemas/";
    private static final Logger LOG = LoggerFactory.getLogger(RestOpenApiReader.class);
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
    public OpenAPI read(
            CamelContext camelContext, List<RestDefinition> rests, BeanConfig config,
            String camelContextId, ClassResolver classResolver)
            throws ClassNotFoundException {

        OpenAPI openApi = new OpenAPI();

        for (RestDefinition rest : rests) {
            Boolean disabled = CamelContextHelper.parseBoolean(camelContext, rest.getDisabled());
            if (disabled == null || !disabled) {
                parse(camelContext, openApi, rest, camelContextId, classResolver, config);
            }
        }

        openApi = shortenClassNames(openApi);

        /*
         * Fixes the problem of not generating the "paths" section when no rest route is defined.
         * A schema with no paths is considered invalid.
         */
        if (openApi.getPaths() == null) {
            openApi.setPaths(new Paths());
        }

        /*
         * Fixes the problem of generating duplicated tags which is invalid per the specification
         */
        if (openApi.getTags() != null) {
            openApi.setTags(new ArrayList<>(
                    openApi.getTags()
                            .stream()
                            .collect(Collectors.toMap(Tag::getName, Function.identity(), (prev, current) -> prev))
                            .values()));
        }

        // configure before returning
        openApi = config.configure(openApi);
        checkCompatOpenApi2(openApi, config);
        return openApi;
    }

    private void checkCompatOpenApi2(OpenAPI openApi, BeanConfig config) {
        if (!config.isOpenApi3()) {
            // Verify that the OpenAPI 3 model can be downgraded to OpenApi 2
            OpenAPI3to2 converter = new OpenAPI3to2();
            converter.convertOpenAPI3to2(openApi);
        }
    }

    private void parse(
            CamelContext camelContext, OpenAPI openApi, RestDefinition rest, String camelContextId,
            ClassResolver classResolver, BeanConfig config)
            throws ClassNotFoundException {

        // only include enabled verbs
        List<VerbDefinition> filter = new ArrayList<>();
        for (VerbDefinition verb : rest.getVerbs()) {
            Boolean disabled = CamelContextHelper.parseBoolean(camelContext, verb.getDisabled());
            if (disabled == null || !disabled) {
                filter.add(verb);
            }
        }
        List<VerbDefinition> verbs = new ArrayList<>(filter);
        // must sort the verbs by uri so we group them together when an uri has multiple operations
        verbs.sort(new VerbOrdering(camelContext));

        // we need to group the operations within the same tag, so use the path as default if not configured
        // Multi tag support for a comma delimeted tag
        String[] pathAsTags = null != rest.getTag()
                ? getValue(camelContext, rest.getTag()).split(",")
                : null != rest.getPath()
                        ? new String[] { getValue(camelContext, rest.getPath()) }
                : new String[0];

        parseOas30(openApi, rest, pathAsTags);

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

        doParseVerbs(camelContext, openApi, rest, camelContextId, verbs, pathAsTags, config);

        // setup root security node if necessary
        List<SecurityDefinition> securityRequirements = rest.getSecurityRequirements();
        securityRequirements.forEach(requirement -> {
            SecurityRequirement oasRequirement = new SecurityRequirement();
            List<String> scopes;
            if (requirement.getScopes() == null || requirement.getScopes().trim().isEmpty()) {
                scopes = Collections.emptyList();
            } else {
                scopes = Arrays.asList(requirement.getScopes().trim().split("\\s*,\\s*"));
            }
            oasRequirement.addList(requirement.getKey(), scopes);
            openApi.addSecurityItem(oasRequirement);
        });
    }

    private void parseOas30(OpenAPI openApi, RestDefinition rest, String[] pathAsTags) {
        String summary = rest.getDescriptionText();

        for (String tag : pathAsTags) {
            // add rest as tag
            openApi.addTagsItem(new Tag().name(tag).description(summary));
        }

        // setup security definitions
        RestSecuritiesDefinition sd = rest.getSecurityDefinitions();
        if (sd != null && !sd.getSecurityDefinitions().isEmpty() && openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        if (sd != null) {
            for (RestSecurityDefinition def : sd.getSecurityDefinitions()) {
                if (def instanceof BasicAuthDefinition) {
                    SecurityScheme auth = new SecurityScheme().type(SecurityScheme.Type.HTTP)
                            .scheme("basic").description(def.getDescription());
                    openApi.getComponents().addSecuritySchemes(def.getKey(), auth);
                } else if (def instanceof BearerTokenDefinition) {
                    SecurityScheme auth = new SecurityScheme().type(SecurityScheme.Type.HTTP)
                            .scheme("bearer").description(def.getDescription())
                            .bearerFormat(((BearerTokenDefinition) def).getFormat());
                    openApi.getComponents().addSecuritySchemes(def.getKey(), auth);
                } else if (def instanceof ApiKeyDefinition) {
                    ApiKeyDefinition rs = (ApiKeyDefinition) def;
                    SecurityScheme auth = new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                            .name(rs.getName()).description(def.getDescription());

                    if (rs.getInHeader() != null && Boolean.parseBoolean(rs.getInHeader())) {
                        auth.setIn(SecurityScheme.In.HEADER);
                    } else if (rs.getInQuery() != null && Boolean.parseBoolean(rs.getInQuery())) {
                        auth.setIn(SecurityScheme.In.QUERY);
                    } else if (rs.getInCookie() != null && Boolean.parseBoolean(rs.getInCookie())) {
                        auth.setIn(SecurityScheme.In.COOKIE);
                    } else {
                        throw new IllegalStateException("No API Key location specified.");
                    }
                    openApi.getComponents().addSecuritySchemes(def.getKey(), auth);
                } else if (def instanceof OAuth2Definition) {
                    OAuth2Definition rs = (OAuth2Definition) def;

                    SecurityScheme auth = new SecurityScheme().type(SecurityScheme.Type.OAUTH2)
                            .description(def.getDescription());
                    String flow = rs.getFlow();
                    if (flow == null) {
                        flow = inferOauthFlow(rs);
                    }
                    OAuthFlows oauthFlows = new OAuthFlows();
                    auth.setFlows(oauthFlows);
                    OAuthFlow oauthFlow = new OAuthFlow();
                    switch (flow) {
                        case "authorizationCode":
                        case "accessCode":
                            oauthFlows.setAuthorizationCode(oauthFlow);
                            break;
                        case "implicit":
                            oauthFlows.setImplicit(oauthFlow);
                            break;
                        case "clientCredentials":
                        case "application":
                            oauthFlows.setClientCredentials(oauthFlow);
                            break;
                        case "password":
                            oauthFlows.setPassword(oauthFlow);
                            break;
                        default:
                            throw new IllegalStateException("Invalid OAuth flow '" + flow + "' specified");
                    }
                    oauthFlow.setAuthorizationUrl(rs.getAuthorizationUrl());
                    oauthFlow.setTokenUrl(rs.getTokenUrl());
                    oauthFlow.setRefreshUrl(rs.getRefreshUrl());
                    if (!rs.getScopes().isEmpty()) {
                        oauthFlow.setScopes(new Scopes());
                        for (RestPropertyDefinition scope : rs.getScopes()) {
                            oauthFlow.getScopes().addString(scope.getKey(), scope.getValue());
                        }
                    }
                    openApi.getComponents().addSecuritySchemes(def.getKey(), auth);
                } else if (def instanceof MutualTLSDefinition) {
                    SecurityScheme auth = new SecurityScheme().type(SecurityScheme.Type.MUTUALTLS)
                            .description(def.getDescription());
                    openApi.getComponents().addSecuritySchemes(def.getKey(), auth);
                } else if (def instanceof OpenIdConnectDefinition) {
                    SecurityScheme auth = new SecurityScheme().type(SecurityScheme.Type.OPENIDCONNECT)
                            .description(def.getDescription());
                    auth.setOpenIdConnectUrl(((OpenIdConnectDefinition) def).getUrl());
                    openApi.getComponents().addSecuritySchemes(def.getKey(), auth);
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
            CamelContext camelContext, OpenAPI openApi, RestDefinition rest, String camelContextId,
            List<VerbDefinition> verbs, String[] pathAsTags, BeanConfig config) {

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

            if (openApi.getPaths() == null) {
                openApi.paths(new Paths());
            }
            PathItem path = openApi.getPaths().get(opPath);
            if (path == null) {
                path = new PathItem();   //openApi.paths.createPathItem(opPath);
            }

            Operation op = new Operation(); //path.createOperation(method);
            for (String tag : pathAsTags) {
                // group in the same tag
                op.addTagsItem(tag);
            }

            // favour ids from verb, rest, route
            final String operationId;
            if (verb.getId() != null) {
                operationId = getValue(camelContext, verb.getId());
            } else if (rest.getId() != null) {
                operationId = getValue(camelContext, rest.getId());
            } else {
                verb.idOrCreate(camelContext.getCamelContextExtension().getContextPlugin(NodeIdFactory.class));
                operationId = verb.getId();
            }
            op.setOperationId(operationId);

            // add id as vendor extensions
            op.addExtension("x-camelContextId", camelContextId);

            path.operation(PathItem.HttpMethod.valueOf(method.toUpperCase()), op);

            String consumes = getValue(camelContext, verb.getConsumes() != null ? verb.getConsumes() : rest.getConsumes());
            if (consumes == null) {
                consumes = config.defaultConsumes;
            }

            String produces = getValue(camelContext, verb.getProduces() != null ? verb.getProduces() : rest.getProduces());
            if (produces == null) {
                produces = config.defaultProduces;
            }

            doParseVerb(camelContext, openApi, verb, op, consumes, produces);
            // enrich with configured response messages from the rest-dsl
            doParseResponseMessages(camelContext, openApi, verb, op, produces);

            // add path
            openApi.getPaths().addPathItem(opPath, path);
        }
    }

    private void doParseVerb(
            CamelContext camelContext, OpenAPI openApi, VerbDefinition verb, Operation op, String consumes,
            String produces) {
        if (verb.getDescriptionText() != null) {
            op.setSummary(getValue(camelContext, verb.getDescriptionText()));
        }

        if ("true".equals(verb.getDeprecated())) {
            op.setDeprecated(Boolean.TRUE);
        }

        // security
        for (SecurityDefinition sd : verb.getSecurity()) {
            List<String> scopes = new ArrayList<>();
            if (sd.getScopes() != null) {
                for (String scope : ObjectHelper.createIterable(getValue(camelContext, sd.getScopes()))) {
                    scopes.add(scope);
                }
            }
            SecurityRequirement securityRequirement = new SecurityRequirement(); //op.createSecurityRequirement();
            securityRequirement.addList(getValue(camelContext, sd.getKey()), scopes);
            op.addSecurityItem(securityRequirement);
        }

        for (ParamDefinition param : verb.getParams()) {
            Parameter parameter = new Parameter().in(param.getType().name());

            if (parameter != null) {
                parameter.setName(getValue(camelContext, param.getName()));
                if (org.apache.camel.util.ObjectHelper.isNotEmpty(param.getDescription())) {
                    parameter.setDescription(getValue(camelContext, param.getDescription()));
                }
                parameter.setRequired(param.getRequired());

                // set type on parameter
                if (!"body".equals(parameter.getIn())) {
                    Schema schema = new Schema<>();
                    final boolean isArray = getValue(camelContext, param.getDataType()).equalsIgnoreCase("array");
                    final List<String> allowableValues = getValue(camelContext, param.getAllowableValuesAsStringList());
                    final boolean hasAllowableValues = allowableValues != null && !allowableValues.isEmpty();
                    if (param.getDataType() != null) {
                        parameter.setSchema(schema);
                        schema.setType(getValue(camelContext, param.getDataType()));
                        if (param.getDataFormat() != null) {
                            schema.setFormat(getValue(camelContext, param.getDataFormat()));
                        }
                        if (isArray) {
                            String arrayType = getValue(camelContext, param.getArrayType());
                            if (arrayType != null) {
                                if (arrayType.equalsIgnoreCase("string")) {
                                    defineSchemas(parameter, allowableValues, String.class);
                                }
                                if (arrayType.equalsIgnoreCase("int") || arrayType.equalsIgnoreCase("integer")) {
                                    defineSchemas(parameter, allowableValues, Integer.class);
                                }
                                if (arrayType.equalsIgnoreCase("long")) {
                                    defineSchemas(parameter, allowableValues, Long.class);
                                }
                                if (arrayType.equalsIgnoreCase("float")) {
                                    defineSchemas(parameter, allowableValues, Float.class);
                                }
                                if (arrayType.equalsIgnoreCase("double")) {
                                    defineSchemas(parameter, allowableValues, Double.class);
                                }
                                if (arrayType.equalsIgnoreCase("boolean")) {
                                    defineSchemas(parameter, allowableValues, Boolean.class);
                                }
                                if (arrayType.equalsIgnoreCase("byte")) {
                                    defineSchemas(parameter, allowableValues, ByteArraySchema.class);
                                }
                                if (arrayType.equalsIgnoreCase("binary")) {
                                    defineSchemas(parameter, allowableValues, BinarySchema.class);
                                }
                                if (arrayType.equalsIgnoreCase("date")) {
                                    defineSchemas(parameter, allowableValues, DateSchema.class);
                                }
                                if (arrayType.equalsIgnoreCase("date-time")) {
                                    defineSchemas(parameter, allowableValues, DateTimeSchema.class);
                                }
                                if (arrayType.equalsIgnoreCase("password")) {
                                    defineSchemas(parameter, allowableValues, PasswordSchema.class);
                                }
                            }
                        }
                    }
                    if (param.getCollectionFormat() != null) {
                        parameter.setStyle(convertToOpenApiStyle(getValue(camelContext, param.getCollectionFormat().name())));
                    }
                    if (hasAllowableValues && !isArray) {
                        schema.setEnum(allowableValues);
                    }

                    // set default value on parameter
                    if (org.apache.camel.util.ObjectHelper.isNotEmpty(param.getDefaultValue())) {
                        schema.setDefault(getValue(camelContext, param.getDefaultValue()));
                    }
                    // add examples
                    if (param.getExamples() != null && !param.getExamples().isEmpty()) {
                        // Examples can be added with a key or a single one with no key
                        for (RestPropertyDefinition example : param.getExamples()) {
                            if (example.getKey().isEmpty()) {
                                if (parameter.getExample() != null) {
                                    LOG.warn("The parameter already has an example with no key!");
                                }
                                parameter.setExample(example.getValue());
                            } else {
                                parameter.addExample(example.getKey(), new Example().value(example.getValue()));
                            }
                        }
                    }
                    op.addParametersItem(parameter);
                }

                // In OpenAPI 3x, body or form parameters are replaced by requestBody
                if (parameter.getIn().equals("body")) {
                    RequestBody reqBody = new RequestBody().content(new Content());
                    reqBody.setRequired(param.getRequired());
                    reqBody.setDescription(getValue(camelContext, param.getDescription()));
                    op.setRequestBody(reqBody);
                    String type = getValue(camelContext, param.getDataType() != null ? param.getDataType() : verb.getType());
                    Schema<?> bodySchema = null;
                    if (type != null) {
                        if (type.endsWith("[]")) {
                            type = type.substring(0, type.length() - 2);

                            //                            Schema arrayModel = (Oas30Schema) bp.createSchema();
                            bodySchema = modelTypeAsProperty(type, openApi);

                        } else {
                            String ref = modelTypeAsRef(type, openApi);
                            if (ref != null) {
                                bodySchema = new Schema().$ref(OAS30_SCHEMA_DEFINITION_PREFIX + ref);
                            } else {
                                bodySchema = modelTypeAsProperty(type, openApi);
                            }
                        }
                    }

                    if (consumes != null) {
                        String[] parts = consumes.split(",");
                        for (String part : parts) {
                            MediaType mediaType = new MediaType().schema(bodySchema);
                            if (param.getExamples() != null) {
                                for (RestPropertyDefinition example : param.getExamples()) {
                                    if (part.equals(example.getKey())) {
                                        mediaType.setExample(example.getValue());
                                    }
                                    // TODO: Check for non-matched or empty key
                                }
                            }
                            reqBody.getContent().addMediaType(part, mediaType);
                        }
                    }
                }

                //                op.addParameter(parameter);
            }
        }

        // clear parameters if its empty
        if (op.getParameters() != null && op.getParameters().isEmpty()) {
            //            op.parameters.clear();
            op.setParameters(null); // Is this necessary?
        }

        // if we have an out type then set that as response message
        if (verb.getOutType() != null) {
            if (op.getResponses() == null) {
                op.setResponses(new ApiResponses());
            }

            String[] parts;
            if (produces != null) {
                parts = produces.split(",");
                for (String produce : parts) {
                    ApiResponse response = new ApiResponse().description("Output type"); // ??
                    Content responseContent = new Content();
                    MediaType contentType = new MediaType();
                    responseContent.addMediaType(produce, contentType);
                    Schema<?> model = modelTypeAsProperty(getValue(camelContext, verb.getOutType()), openApi);
                    contentType.setSchema(model);
                    response.setContent(responseContent);
                    // response.description = "Output type";
                    //                    op.responses.addResponse("200", response);
                    op.getResponses().addApiResponse("200", response);
                }
            }
        }

    }

    private StyleEnum convertToOpenApiStyle(String value) {
        //Should be a Collection Format name
        switch (CollectionFormat.valueOf(value)) {
            case csv:
                return StyleEnum.FORM;
            case ssv:
            case tsv:
                return StyleEnum.SPACEDELIMITED;
            case pipes:
                return StyleEnum.PIPEDELIMITED;
            case multi:
                return StyleEnum.DEEPOBJECT;
            default:
                return null;
        }
    }

    private static void defineSchemas(
            final Parameter serializableParameter,
            final List<String> allowableValues,
            final Class<?> type) {
        Schema parameterSchema = serializableParameter.getSchema();
        if (allowableValues != null && !allowableValues.isEmpty()) {
            if (String.class.equals(type)) {
                parameterSchema.setEnum(allowableValues);
            } else {
                convertAndSetItemsEnum(parameterSchema, allowableValues, type);
            }
        } else if (Objects.equals(parameterSchema.getType(), "array")) {

            Schema<?> itemsSchema;

            if (Integer.class.equals(type)) {
                itemsSchema = new IntegerSchema();
            } else if (Long.class.equals(type)) {
                itemsSchema = new IntegerSchema().format("int64");
            } else if (Float.class.equals(type)) {
                itemsSchema = new NumberSchema().format("float");
            } else if (Double.class.equals(type)) {
                itemsSchema = new NumberSchema().format("double");
            } else if (ByteArraySchema.class.equals(type)) {
                itemsSchema = new ByteArraySchema();
            } else if (BinarySchema.class.equals(type)) {
                itemsSchema = new BinarySchema();
            } else if (Date.class.equals(type)) {
                itemsSchema = new DateSchema();
            } else if (DateTimeSchema.class.equals(type)) {
                itemsSchema = new DateTimeSchema();
            } else if (PasswordSchema.class.equals(type)) {
                itemsSchema = new PasswordSchema();
            } else {
                itemsSchema = new StringSchema();
            }

            parameterSchema.setItems(itemsSchema);
        }
    }

    private static void convertAndSetItemsEnum(
            final Schema items, final List<String> allowableValues,
            final Class<?> type) {
        try {
            final MethodHandle valueOf = publicLookup().findStatic(type, "valueOf",
                    MethodType.methodType(type, String.class));
            final MethodHandle setEnum = publicLookup().bind(items, "setEnum",
                    MethodType.methodType(void.class, List.class));
            final List<?> values = allowableValues.stream().map(v -> {
                try {
                    return valueOf.invoke(v);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Throwable e) {
                    throw new IllegalStateException(e);
                }
            }).collect(Collectors.toList());
            setEnum.invoke(values);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    private void doParseResponseMessages(
            CamelContext camelContext, OpenAPI openApi, VerbDefinition verb, Operation op, String produces) {
        if (op.getResponses() == null) {
            op.setResponses(new ApiResponses());
        }
        for (ResponseMessageDefinition msg : verb.getResponseMsgs()) {
            doParseResponse(camelContext, openApi, op, produces, msg);
        }

        // must include an empty noop response if none exists
        if (op.getResponses().isEmpty()) {
            op.getResponses().setDefault(new ApiResponse());
        }
    }

    private void doParseResponse(
            CamelContext camelContext, OpenAPI openApi, Operation op, String produces,
            ResponseMessageDefinition msg) {
        ApiResponse response = null;

        String code = getValue(camelContext, msg.getCode());
        response = op.getResponses().get(code);
        if (response == null) {
            response = new ApiResponse();
            op.getResponses().addApiResponse(code, response);
        }

        if (org.apache.camel.util.ObjectHelper.isNotEmpty(msg.getResponseModel())) {
            String[] parts;
            if (produces != null) {
                Content respContent = new Content();
                parts = produces.split(",");
                for (String produce : parts) {
                    Schema model = modelTypeAsProperty(getValue(camelContext, msg.getResponseModel()), openApi);
                    respContent.addMediaType(produce, new MediaType().schema(model));
                }
                response.setContent(respContent);
            }
        }
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(msg.getMessage())) {
            response.setDescription(getValue(camelContext, msg.getMessage()));
        }

        // add headers
        if (msg.getHeaders() != null) {
            for (ResponseHeaderDefinition header : msg.getHeaders()) {
                String name = getValue(camelContext, header.getName());
                String type = getValue(camelContext, header.getDataType());
                String format = getValue(camelContext, header.getDataFormat());

                if ("string".equals(type) || "long".equals(type) || "float".equals(type)
                        || "double".equals(type) || "boolean".equals(type)) {
                    setResponseHeader(camelContext, response, header, name, format, type);
                } else if ("int".equals(type) || "integer".equals(type)) {
                    setResponseHeader(camelContext, response, header, name, format, "integer");
                } else if ("array".equals(type)) {
                    Header ap = new Header();
                    response.addHeaderObject(name, ap);
                    if (org.apache.camel.util.ObjectHelper.isNotEmpty(header.getDescription())) {
                        ap.setDescription(getValue(camelContext, header.getDescription()));
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
                        ap.addExample("", new Example().value(getValue(camelContext, header.getExample())));
                    }
                }
            }
        }

        // add examples
        if (msg.getExamples() != null) {
            if (response.getContent() != null) {
                for (MediaType mediaType : response.getContent().values()) {
                    for (RestPropertyDefinition prop : msg.getExamples()) {
                        mediaType.addExamples(getValue(camelContext, prop.getKey()), new Example()
                                .value(getValue(camelContext, prop.getValue())));
                    }
                }
            }
            // if no content, can't add examples!
        }
    }

    private void setHeaderSchemaOas30(Header ap, String arrayType) {
        Schema items = new Schema().type(arrayType);
        ap.setSchema(items);
    }

    private void setResponseHeader(
            CamelContext camelContext, ApiResponse response, ResponseHeaderDefinition header,
            String name, String format, String type) {
        Header ip = new Header();
        response.addHeaderObject(name, ip);
        Schema schema = new Schema().type(type);
        ip.setSchema(schema);
        if (format != null) {
            schema.setFormat(format);
        }
        ip.setDescription(getValue(camelContext, header.getDescription()));

        List<String> values;
        if (header.getAllowableValues() != null) {
            values = new ArrayList<>();
            for (String text : header.getAllowableValuesAsStringList()) {
                values.add(getValue(camelContext, text));
            }
            schema.setEnum(values);
        }
        // add example
        if (header.getExample() != null) {
            ip.addExample("", new Example().value(getValue(camelContext, header.getExample())));
        }
    }

    private String modelTypeAsRef(String typeName, OpenAPI openApi) {
        boolean array = typeName.endsWith("[]");
        if (array) {
            typeName = typeName.substring(0, typeName.length() - 2);
        }

        if (NO_REFERENCE_TYPE_NAMES.contains(typeName)) {
            return null;
        }

        if (openApi.getComponents() != null
                && openApi.getComponents().getSchemas() != null) {
            for (Schema model : openApi.getComponents().getSchemas().values()) {
                if (typeName.equals(getClassNameExtension(model))) {
                    return model.getName();
                }
            }
        }
        return null;
    }

    private Object getClassNameExtension(Schema model) {
        Object className = null;
        if (model.getExtensions() != null) {
            Object value = model.getExtensions().get("x-className");
            if (value instanceof Map) {
                className = ((Map) value).get("format");
            }
        }
        return className;
    }

    private Schema<?> modelTypeAsProperty(String typeName, OpenAPI openApi) {
        Schema<?> prop = null;
        boolean array = typeName.endsWith("[]");
        if (array) {
            typeName = typeName.substring(0, typeName.length() - 2);
        }

        String ref = modelTypeAsRef(typeName, openApi);

        if (ref == null) {
            // No explicit schema reference so handle primitive types
            // special for byte arrays
            if (array && ("byte".equals(typeName) || "java.lang.Byte".equals(typeName))) {
                // Note built-in ByteArraySchema sets type="string" !
                prop = new Schema<byte[]>().type("number").format("byte");
                array = false;
            } else if ("string".equalsIgnoreCase(typeName) || "java.lang.String".equals(typeName)) {
                prop = new StringSchema();
            } else if ("int".equals(typeName) || "java.lang.Integer".equals(typeName)) {
                prop = new IntegerSchema();
            } else if ("long".equals(typeName) || "java.lang.Long".equals(typeName)) {
                prop = new IntegerSchema().format("int64");
            } else if ("float".equals(typeName) || "java.lang.Float".equals(typeName)) {
                prop = new NumberSchema().format("float");
            } else if ("double".equals(typeName) || "java.lang.Double".equals(typeName)) {
                prop = new NumberSchema().format("double");
            } else if ("boolean".equals(typeName) || "java.lang.Boolean".equals(typeName)) {
                prop = new NumberSchema().format("boolean");
            } else if ("file".equals(typeName) || "java.io.File".equals(typeName)) {
                prop = new FileSchema();
            } else {
                prop = new StringSchema();
            }
        }

        if (array) {
            Schema<?> items = new Schema<>();
            if (ref != null) {
                items.set$ref(OAS30_SCHEMA_DEFINITION_PREFIX + ref);
            }
            prop = new ArraySchema().items(items);
        } else if (prop == null) {
            prop = new Schema<>().$ref(OAS30_SCHEMA_DEFINITION_PREFIX + ref);
        }
        return prop;
    }

    /**
     * If the class is annotated with openApi annotations its parsed into a OpenApi model representation which is added
     * to openApi
     *
     * @param clazz   the class such as pojo with openApi annotation
     * @param openApi the openApi model
     */
    private void appendModels(Class<?> clazz, OpenAPI openApi) {
        RestModelConverters converters = new RestModelConverters();
        List<? extends Schema<?>> models = converters.readClass(openApi, clazz);
        if (models == null) {
            return;
        }
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        for (Schema<?> newSchema : models) {
            // favor keeping any existing model that has the vendor extension in the model
            boolean addSchema = true;
            if (openApi.getComponents().getSchemas() != null) {
                Schema<?> existing = openApi.getComponents().getSchemas().get(newSchema.getName());
                if (existing != null) {
                    // check classname extension
                    Object oldClassName = getClassNameExtension(existing);
                    Object newClassName = getClassNameExtension(newSchema);
                    if (oldClassName != null) {
                        // a schema with this name and a classname is already in the model
                        addSchema = false;
                        LOG.info("Duplicate schema found for with name {}; classname1={}, classname2={}",
                                newSchema.getName(), oldClassName,
                                newClassName != null ? newClassName : "none");
                    }
                }
            }
            if (addSchema) {
                openApi.getComponents().addSchemas(newSchema.getName(), newSchema);
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

    private OpenAPI shortenClassNames(OpenAPI document) {
        if (document.getComponents() == null || document.getComponents().getSchemas() == null) {
            return document;
        }

        // Make a mapping from full name to possibly shortened name.
        Map<String, String> names = new HashMap<>();
        Stream<String> schemaStream = document.getComponents().getSchemas().keySet().stream();

        schemaStream.forEach(key -> {
            String s = key.replaceAll("[^a-zA-Z0-9.-_]", "_");
            String shortName = s.substring(s.lastIndexOf('.') + 1);
            names.put(key, names.containsValue(shortName) ? s : shortName);
        });
        // Remap the names
        for (Map.Entry<String, String> namePair : names.entrySet()) {
            Schema<?> schema = document.getComponents().getSchemas().get(namePair.getKey());
            if (schema != null) {
                document.getComponents().getSchemas().remove(namePair.getKey());
                document.getComponents().addSchemas(namePair.getValue(), schema);
            }
        }

        AbstractSpecFilter filter = new SchemaFilter(names);
        return new SpecFilter().filter(document, filter, null, null, null);

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

    private static class SchemaFilter extends AbstractSpecFilter {
        private final Map<String, String> names;

        SchemaFilter(Map<String, String> names) {
            this.names = names;
        }

        @Override
        public Optional<Parameter> filterParameter(
                Parameter parameter, Operation operation, ApiDescription api,
                Map<String, List<String>> params, Map<String, String> cookies, Map<String, List<String>> headers) {
            if (parameter.getContent() != null) {
                processRefsInContent(parameter.getContent(), params, cookies, headers);
            }
            return Optional.of(parameter);
        }

        @Override
        public Optional<RequestBody> filterRequestBody(
                RequestBody requestBody, Operation operation, ApiDescription api,
                Map<String, List<String>> params, Map<String, String> cookies, Map<String, List<String>> headers) {
            if (requestBody.getContent() != null) {
                processRefsInContent(requestBody.getContent(), params, cookies, headers);
            }
            return Optional.of(requestBody);
        }

        @Override
        public Optional<ApiResponse> filterResponse(
                ApiResponse response, Operation operation, ApiDescription api,
                Map<String, List<String>> params, Map<String, String> cookies, Map<String, List<String>> headers) {
            if (response.getContent() != null) {
                processRefsInContent(response.getContent(), params, cookies, headers);
            }
            return Optional.of(response);
        }

        @SuppressWarnings("rawtypes")
        @Override
        public Optional<Schema> filterSchema(
                Schema schema,
                Map<String, List<String>> params, Map<String, String> cookies, Map<String, List<String>> headers) {
            if (schema.getName() != null) {
                schema.setName(fixSchemaReference(schema.getName(), OAS30_SCHEMA_DEFINITION_PREFIX));
            }
            if (schema.get$ref() != null) {
                schema.set$ref(fixSchemaReference(schema.get$ref(), OAS30_SCHEMA_DEFINITION_PREFIX));
            }
            if (schema.getItems() != null) {
                filterSchema(schema.getItems(), params, cookies, headers);
            }
            if (schema.getProperties() != null) {
                for (Schema<?> propSchema : (Collection<Schema>) schema.getProperties().values()) {
                    filterSchema(propSchema, params, cookies, headers);
                }
            }
            if (schema.getAdditionalProperties() != null &&
                    schema.getAdditionalProperties() instanceof Schema) {
                filterSchema((Schema) schema.getAdditionalProperties(), params, cookies, headers);
            }
            if (schema.getAnyOf() != null) {
                List<Schema> any = schema.getAnyOf();
                for (Schema child : any) {
                    filterSchema(child, params, cookies, headers);
                }
            }
            if (schema.getAllOf() != null) {
                List<Schema> all = schema.getAllOf();
                for (Schema child : all) {
                    filterSchema(child, params, cookies, headers);
                }
            }
            if (schema.getOneOf() != null) {
                List<Schema> oneOf = schema.getOneOf();
                for (Schema child : oneOf) {
                    filterSchema(child, params, cookies, headers);
                }
            }
            return Optional.of(schema);
        }

        private void processRefsInContent(
                Content content, Map<String, List<String>> params,
                Map<String, String> cookies, Map<String, List<String>> headers) {
            for (MediaType media : content.values()) {
                if (media.getSchema() != null) {
                    filterSchema(media.getSchema(), params, cookies, headers);
                }
            }
        }

        private String fixSchemaReference(String ref, String prefix) {
            if (ref.startsWith(prefix)) {
                ref = ref.substring(prefix.length());
            }

            String name = names.get(ref);
            return name == null ? ref : name;
        }
    }

}
