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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.apicurio.datamodels.core.models.ExtensibleNode;
import io.apicurio.datamodels.core.models.Extension;
import io.apicurio.datamodels.core.models.common.AuthorizationCodeOAuthFlow;
import io.apicurio.datamodels.core.models.common.ImplicitOAuthFlow;
import io.apicurio.datamodels.core.models.common.OAuthFlow;
import io.apicurio.datamodels.core.models.common.SecurityRequirement;
import io.apicurio.datamodels.openapi.models.OasDocument;
import io.apicurio.datamodels.openapi.models.OasOperation;
import io.apicurio.datamodels.openapi.models.OasParameter;
import io.apicurio.datamodels.openapi.models.OasPathItem;
import io.apicurio.datamodels.openapi.models.OasSchema;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v2.models.Oas20Header;
import io.apicurio.datamodels.openapi.v2.models.Oas20Items;
import io.apicurio.datamodels.openapi.v2.models.Oas20Operation;
import io.apicurio.datamodels.openapi.v2.models.Oas20Parameter;
import io.apicurio.datamodels.openapi.v2.models.Oas20Response;
import io.apicurio.datamodels.openapi.v2.models.Oas20Schema;
import io.apicurio.datamodels.openapi.v2.models.Oas20SchemaDefinition;
import io.apicurio.datamodels.openapi.v2.models.Oas20SecurityScheme;
import io.apicurio.datamodels.openapi.v3.models.Oas30Document;
import io.apicurio.datamodels.openapi.v3.models.Oas30Header;
import io.apicurio.datamodels.openapi.v3.models.Oas30MediaType;
import io.apicurio.datamodels.openapi.v3.models.Oas30Operation;
import io.apicurio.datamodels.openapi.v3.models.Oas30Parameter;
import io.apicurio.datamodels.openapi.v3.models.Oas30Response;
import io.apicurio.datamodels.openapi.v3.models.Oas30Schema;
import io.apicurio.datamodels.openapi.v3.models.Oas30SchemaDefinition;
import io.apicurio.datamodels.openapi.v3.models.Oas30SecurityScheme;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestOperationParamDefinition;
import org.apache.camel.model.rest.RestOperationResponseHeaderDefinition;
import org.apache.camel.model.rest.RestOperationResponseMsgDefinition;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.model.rest.RestPropertyDefinition;
import org.apache.camel.model.rest.RestSecuritiesDefinition;
import org.apache.camel.model.rest.RestSecurityApiKey;
import org.apache.camel.model.rest.RestSecurityBasicAuth;
import org.apache.camel.model.rest.RestSecurityDefinition;
import org.apache.camel.model.rest.RestSecurityOAuth2;
import org.apache.camel.model.rest.SecurityDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.FileUtil;

import static java.lang.invoke.MethodHandles.publicLookup;

/**
 * A Camel REST-DSL openApi reader that parse the rest-dsl into a openApi model representation.
 * <p/>
 * This reader supports the <a href="https://www.openapis.org/">OpenApi Specification 2.0 and 3.0</a>
 */
public class RestOpenApiReader {

    /**
     * Read the REST-DSL definition's and parse that as a OpenApi model representation
     *
     * @param rests the rest-dsl
     * @param route optional route path to filter the rest-dsl to only include from the chose route
     * @param config the openApi configuration
     * @param classResolver class resolver to use
     * @return the openApi model
     * @throws ClassNotFoundException
     */
    public OasDocument read(List<RestDefinition> rests, String route, BeanConfig config,
                            String camelContextId, ClassResolver classResolver)
        throws ClassNotFoundException {
        OasDocument openApi = null;
        if (config.isOpenApi3()) {
            openApi = new Oas30Document();
        } else {
            openApi = new Oas20Document();
        }
        for (RestDefinition rest : rests) {

            if (org.apache.camel.util.ObjectHelper.isNotEmpty(route) && !route.equals("/")) {
                // filter by route
                if (!rest.getPath().equals(route)) {
                    continue;
                }
            }

            parse(openApi, rest, camelContextId, classResolver);
        }

        // configure before returning
        openApi = config.configure(openApi);
        return openApi;
    }

    private void parse(OasDocument openApi, RestDefinition rest, String camelContextId,
                       ClassResolver classResolver)
        throws ClassNotFoundException {
        List<VerbDefinition> verbs = new ArrayList<>(rest.getVerbs());
        // must sort the verbs by uri so we group them together when an uri has multiple operations
        Collections.sort(verbs, new VerbOrdering());
        // we need to group the operations within the same tag, so use the path as default if not
        // configured
        String pathAsTag = rest.getTag() != null
            ? rest.getTag() : FileUtil.stripLeadingSeparator(rest.getPath());
        if (openApi instanceof Oas20Document) {
            
            
            parseOas20((Oas20Document)openApi, rest, pathAsTag);

            
        } else if (openApi instanceof Oas30Document) {
            

            
            parseOas30((Oas30Document)openApi, rest, pathAsTag);

            
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
                for (RestOperationResponseMsgDefinition def : verb.getResponseMsgs()) {
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

        doParseVerbs(openApi, rest, camelContextId, verbs, pathAsTag);
    }

    private void parseOas30(Oas30Document openApi, RestDefinition rest, String pathAsTag) {
        String summary = rest.getDescriptionText();

        if (org.apache.camel.util.ObjectHelper.isNotEmpty(pathAsTag)) {
            // add rest as tag
            openApi.addTag(pathAsTag, summary);
        }

        // setup security definitions
        RestSecuritiesDefinition sd = rest.getSecurityDefinitions();
        if (sd != null && sd.getSecurityDefinitions().size() != 0 
            && openApi.components == null) {
            openApi.components = openApi
                .createComponents();
        }
        if (sd != null) {
            for (RestSecurityDefinition def : sd.getSecurityDefinitions()) {
                if (def instanceof RestSecurityBasicAuth) {
                    Oas30SecurityScheme auth = openApi.components
                        .createSecurityScheme(def.getKey());
                    auth.type = "http";
                    auth.scheme = "basic";
                    auth.description = def.getDescription();
                    openApi.components.addSecurityScheme("BasicAuth", auth);
                } else if (def instanceof RestSecurityApiKey) {
                    RestSecurityApiKey rs = (RestSecurityApiKey)def;
                    Oas30SecurityScheme auth = openApi.components
                        .createSecurityScheme(def.getKey());
                    auth.type = "apiKey";
                    auth.description = rs.getDescription();
                    auth.name = rs.getName();
                    if (rs.getInHeader() != null && Boolean.parseBoolean(rs.getInHeader())) {
                        auth.in = "header";
                    } else {
                        auth.in = "query";
                    }
                    openApi.components.addSecurityScheme(def.getKey(), auth);
                } else if (def instanceof RestSecurityOAuth2) {
                    RestSecurityOAuth2 rs = (RestSecurityOAuth2)def;

                    Oas30SecurityScheme auth = openApi.components
                        .createSecurityScheme(def.getKey());
                    auth.type = "oauth2";
                    auth.description = rs.getDescription();
                    String flow = rs.getFlow();
                    if (flow == null) {
                        if (rs.getAuthorizationUrl() != null && rs.getTokenUrl() != null) {
                            flow = "accessCode";
                        } else if (rs.getTokenUrl() == null && rs.getAuthorizationUrl() != null) {
                            flow = "implicit";
                        }
                    }
                    OAuthFlow oauthFlow = null;
                    if (auth.flows == null) {
                        auth.flows = auth.createOAuthFlows();
                    }
                    if (flow.equals("accessCode")) {
                        oauthFlow = auth.flows.createAuthorizationCodeOAuthFlow();
                        auth.flows.authorizationCode = (AuthorizationCodeOAuthFlow)oauthFlow;
                    } else if (flow.equals("implicit")) {
                        oauthFlow = auth.flows.createImplicitOAuthFlow();
                        auth.flows.implicit = (ImplicitOAuthFlow)oauthFlow;
                    }
                    oauthFlow.authorizationUrl = rs.getAuthorizationUrl();
                    oauthFlow.tokenUrl = rs.getTokenUrl();
                    for (RestPropertyDefinition scope : rs.getScopes()) {
                        oauthFlow.addScope(scope.getKey(), scope.getValue());
                    }
                    
                    openApi.components.addSecurityScheme(def.getKey(), auth);
                }
            }
        }
    }

    private void parseOas20(Oas20Document openApi, RestDefinition rest, String pathAsTag) {
        String summary = rest.getDescriptionText();

        if (org.apache.camel.util.ObjectHelper.isNotEmpty(pathAsTag)) {
            // add rest as tag
            openApi.addTag(pathAsTag, summary);
        }

        // setup security definitions
        RestSecuritiesDefinition sd = rest.getSecurityDefinitions();
        if (sd != null && sd.getSecurityDefinitions().size() != 0 
            && openApi.securityDefinitions == null) {
            openApi.securityDefinitions = openApi
                .createSecurityDefinitions();
        }
        if (sd != null) {
            for (RestSecurityDefinition def : sd.getSecurityDefinitions()) {
                if (def instanceof RestSecurityBasicAuth) {
                    Oas20SecurityScheme auth = openApi.securityDefinitions
                        .createSecurityScheme(def.getKey());
                    auth.type = "basicAuth";
                    auth.description = def.getDescription();
                    openApi.securityDefinitions.addSecurityScheme("BasicAuth", auth);
                } else if (def instanceof RestSecurityApiKey) {
                    RestSecurityApiKey rs = (RestSecurityApiKey)def;
                    Oas20SecurityScheme auth = openApi.securityDefinitions
                        .createSecurityScheme(def.getKey());
                    auth.type = "apiKey";
                    auth.description = rs.getDescription();
                    auth.name = rs.getName();
                    if (rs.getInHeader() != null && Boolean.parseBoolean(rs.getInHeader())) {
                        auth.in = "header";
                    } else {
                        auth.in = "query";
                    }
                    openApi.securityDefinitions.addSecurityScheme(def.getKey(), auth);
                } else if (def instanceof RestSecurityOAuth2) {
                    RestSecurityOAuth2 rs = (RestSecurityOAuth2)def;

                    Oas20SecurityScheme auth = openApi.securityDefinitions
                        .createSecurityScheme(def.getKey());
                    auth.type = "oauth2";
                    auth.description = rs.getDescription();
                    String flow = rs.getFlow();
                    if (flow == null) {
                        if (rs.getAuthorizationUrl() != null && rs.getTokenUrl() != null) {
                            flow = "accessCode";
                        } else if (rs.getTokenUrl() == null && rs.getAuthorizationUrl() != null) {
                            flow = "implicit";
                        }
                    }
                    auth.flow = flow;
                    auth.authorizationUrl = rs.getAuthorizationUrl();
                    auth.tokenUrl = rs.getTokenUrl();
                    if (rs.getScopes().size() != 0 && auth.scopes == null) {
                        auth.scopes = auth.createScopes();
                    }
                    for (RestPropertyDefinition scope : rs.getScopes()) {
                        auth.scopes.addScope(scope.getKey(), scope.getValue());
                    }
                    if (openApi.securityDefinitions == null) {
                        openApi.securityDefinitions = openApi
                            .createSecurityDefinitions();
                    }
                    openApi.securityDefinitions.addSecurityScheme(def.getKey(), auth);
                }
            }
        }
    }

    private void doParseVerbs(OasDocument openApi, RestDefinition rest, String camelContextId,
                              List<VerbDefinition> verbs, String pathAsTag) {
        // used during gathering of apis

        String basePath = rest.getPath();

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

            // the method must be in lower case
            String method = verb.asVerb().toLowerCase(Locale.US);
            // operation path is a key
            String opPath = OpenApiHelper.buildUrl(basePath, verb.getUri());

            if (openApi.paths == null) {
                openApi.paths = openApi.createPaths();
            }
            OasPathItem path = openApi.paths.getPathItem(opPath);
            if (path == null) {
                path = openApi.paths.createPathItem(opPath);
            }

            OasOperation op = (OasOperation)path.createOperation(method);
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(pathAsTag)) {
                // group in the same tag
                if (op.tags == null) {
                    op.tags = new ArrayList<String>();
                }
                op.tags.add(pathAsTag);
            }

            final String routeId = verb.getRouteId();
            // favour ids from verb, rest, route
            final String operationId;
            if (verb.getId() != null) {
                operationId = verb.getId();
            } else if (rest.getId() != null) {
                operationId = rest.getId();
            } else {
                operationId = routeId;
            }
            op.operationId = operationId;

            // add id as vendor extensions
            Extension extension = op.createExtension();
            extension.name = "x-camelContextId";
            extension.value = camelContextId;
            op.addExtension(extension.name, extension);
            extension = op.createExtension();
            extension.name = "x-routeId";
            extension.value = routeId;
            op.addExtension(extension.name, extension);
            path = setPathOperation(path, op, method);

            String consumes = verb.getConsumes() != null ? verb.getConsumes() : rest.getConsumes();
            String produces = verb.getProduces() != null ? verb.getProduces() : rest.getProduces();
            if (openApi instanceof Oas20Document) {
                doParseVerbOas20((Oas20Document)openApi, verb, (Oas20Operation)op, consumes, produces);
            } else if (openApi instanceof Oas30Document) {
                doParseVerbOas30((Oas30Document)openApi, verb, (Oas30Operation)op, consumes, produces);
            }
            // enrich with configured response messages from the rest-dsl
            doParseResponseMessages(openApi, verb, op, produces);

            // add path
            openApi.paths.addPathItem(opPath, path);

        }
    }

    private void doParseVerbOas30(Oas30Document openApi, VerbDefinition verb, Oas30Operation op, String consumes,
                               String produces) {

        if (verb.getDescriptionText() != null) {
            op.summary = verb.getDescriptionText();
        }

        // security
        for (SecurityDefinition sd : verb.getSecurity()) {
            List<String> scopes = new ArrayList<>();
            if (sd.getScopes() != null) {
                for (String scope : ObjectHelper.createIterable(sd.getScopes())) {
                    scopes.add(scope);
                }
            }
            SecurityRequirement securityRequirement = op.createSecurityRequirement();
            securityRequirement.addSecurityRequirementItem(sd.getKey(), scopes);
            op.addSecurityRequirement(securityRequirement);
        }

        for (RestOperationParamDefinition param : verb.getParams()) {
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
                parameter.name = param.getName();
                if (org.apache.camel.util.ObjectHelper.isNotEmpty(param.getDescription())) {
                    parameter.description = param.getDescription();
                }
                parameter.required = param.getRequired();

                // set type on parameter
                if (!parameter.in.equals("body")) {

                    Oas30Parameter parameter30 = (Oas30Parameter)parameter;
                    Oas30Schema oas30Schema = null;
                    final boolean isArray = param.getDataType().equalsIgnoreCase("array");
                    final List<String> allowableValues = param.getAllowableValues();
                    final boolean hasAllowableValues = allowableValues != null && !allowableValues.isEmpty();
                    if (param.getDataType() != null) {
                        parameter30.schema = parameter30.createSchema();
                        oas30Schema = (Oas30Schema)parameter30.schema;
                        oas30Schema.type = param.getDataType();
                        if (param.getDataFormat() != null) {
                            oas30Schema.format = param.getDataFormat();
                        }
                        if (isArray) {
                            if (param.getArrayType() != null) {
                                if (param.getArrayType().equalsIgnoreCase("string")) {
                                    defineSchemas(parameter30, allowableValues, String.class);
                                }
                                if (param.getArrayType().equalsIgnoreCase("int")
                                    || param.getArrayType().equalsIgnoreCase("integer")) {
                                    defineSchemas(parameter30, allowableValues, Integer.class);
                                }
                                if (param.getArrayType().equalsIgnoreCase("long")) {
                                    defineSchemas(parameter30, allowableValues, Long.class);
                                }
                                if (param.getArrayType().equalsIgnoreCase("float")) {
                                    defineSchemas(parameter30, allowableValues, Float.class);
                                }
                                if (param.getArrayType().equalsIgnoreCase("double")) {
                                    defineSchemas(parameter30, allowableValues, Double.class);
                                }
                                if (param.getArrayType().equalsIgnoreCase("boolean")) {
                                    defineSchemas(parameter30, allowableValues, Boolean.class);
                                }
                            }
                        }
                    }
                    if (param.getCollectionFormat() != null) {
                        parameter30.style = param.getCollectionFormat().name();
                    }
                    if (hasAllowableValues && !isArray) {
                        oas30Schema.enum_ = allowableValues;
                    }

                    // set default value on parameter
                    if (org.apache.camel.util.ObjectHelper.isNotEmpty(param.getDefaultValue())) {
                        oas30Schema.default_ = param.getDefaultValue();
                    }
                    // add examples
                    if (param.getExamples() != null && param.getExamples().size() >= 1) {
                        // we can only set one example on the parameter
                        Extension exampleExtension = parameter30.createExtension();
                        boolean emptyKey = param.getExamples().get(0).getKey().length() == 0;
                        if (emptyKey) {
                            exampleExtension.name = "x-example";
                            exampleExtension.value = param.getExamples().get(0).getValue();
                            parameter30.addExtension("x-example", exampleExtension);
                        } else {
                            Map<String, String> exampleValue = new HashMap<String, String>();
                            exampleValue.put(param.getExamples().get(0).getKey(),
                                             param.getExamples().get(0).getValue());
                            exampleExtension.name = "x-examples";
                            exampleExtension.value = exampleValue;
                            parameter30.addExtension("x-examples", exampleExtension);
                        }
                    }
                }

                // set schema on body parameter
                if (parameter.in.equals("body")) {

                    Oas30Parameter bp = (Oas30Parameter)parameter;

                    String type = param.getDataType() != null ? param.getDataType() : verb.getType();
                    if (type != null) {
                        if (type.endsWith("[]")) {
                            type = type.substring(0, type.length() - 2);

                            OasSchema arrayModel = (Oas30Schema)bp.createSchema();
                            arrayModel = modelTypeAsProperty(type, openApi, arrayModel);
                            bp.schema = arrayModel;

                        } else {
                            String ref = modelTypeAsRef(type, openApi);
                            if (ref != null) {
                                Oas30Schema refModel = (Oas30Schema)bp.createSchema();
                                refModel.$ref = "#/components/schemas/" + ref;
                                bp.schema = refModel;
                            } else {
                                OasSchema model = (Oas30Schema)bp.createSchema();
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
                            op.requestBody.description = param.getDescription();
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
                            exampleExtension.value = param.getExamples().get(0).getValue();
                            op.requestBody.addExtension("x-example", exampleExtension);
                        } else {
                            Map<String, String> exampleValue = new HashMap<String, String>();
                            exampleValue.put(param.getExamples().get(0).getKey(),
                                             param.getExamples().get(0).getValue());
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
            Oas30Response response = (Oas30Response)op.responses.createResponse("200");
            String[] parts = null;
            if (produces != null) {
                parts = produces.split(",");
                for (String produce : parts) {
                    Oas30MediaType contentType = response.createMediaType(produce);
                    response.addMediaType(produce, contentType);
                    OasSchema model = contentType.createSchema();
                    model = modelTypeAsProperty(verb.getOutType(), openApi, model);
                    contentType.schema = (Oas30Schema)model;
                    response.description = "Output type";
                    op.responses.addResponse("200", response);
                }
            }

        }
    }

    private void doParseVerbOas20(Oas20Document openApi, VerbDefinition verb, Oas20Operation op, String consumes,
                               String produces) {
        if (consumes != null) {
            String[] parts = consumes.split(",");
            if (op.consumes == null) {
                op.consumes = new ArrayList<String>();
            }
            for (String part : parts) {
                op.consumes.add(part);
            }
        }

        if (produces != null) {
            String[] parts = produces.split(",");
            if (op.produces == null) {
                op.produces = new ArrayList<String>();
            }
            for (String part : parts) {
                op.produces.add(part);
            }
        }

        if (verb.getDescriptionText() != null) {
            op.summary = verb.getDescriptionText();
        }

        // security
        for (SecurityDefinition sd : verb.getSecurity()) {
            List<String> scopes = new ArrayList<>();
            if (sd.getScopes() != null) {
                for (String scope : ObjectHelper.createIterable(sd.getScopes())) {
                    scopes.add(scope);
                }
            }
            SecurityRequirement securityRequirement = op.createSecurityRequirement();
            securityRequirement.addSecurityRequirementItem(sd.getKey(), scopes);
            op.addSecurityRequirement(securityRequirement);
        }

        for (RestOperationParamDefinition param : verb.getParams()) {
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
                parameter.name = param.getName();
                if (org.apache.camel.util.ObjectHelper.isNotEmpty(param.getDescription())) {
                    parameter.description = param.getDescription();
                }
                parameter.required = param.getRequired();

                // set type on parameter
                if (!parameter.in.equals("body")) {

                    Oas20Parameter serializableParameter = (Oas20Parameter)parameter;
                    final boolean isArray = param.getDataType().equalsIgnoreCase("array");
                    final List<String> allowableValues = param.getAllowableValues();
                    final boolean hasAllowableValues = allowableValues != null && !allowableValues.isEmpty();
                    if (param.getDataType() != null) {
                        serializableParameter.type = param.getDataType();
                        if (param.getDataFormat() != null) {
                            serializableParameter.format = param.getDataFormat();
                        }
                        if (isArray) {
                            if (param.getArrayType() != null) {
                                if (param.getArrayType().equalsIgnoreCase("string")) {
                                    defineItems(serializableParameter, allowableValues, new Oas20Items(),
                                                String.class);
                                }
                                if (param.getArrayType().equalsIgnoreCase("int")
                                    || param.getArrayType().equalsIgnoreCase("integer")) {
                                    defineItems(serializableParameter, allowableValues, new Oas20Items(),
                                                Integer.class);
                                }
                                if (param.getArrayType().equalsIgnoreCase("long")) {
                                    defineItems(serializableParameter, allowableValues, new Oas20Items(),
                                                Long.class);
                                }
                                if (param.getArrayType().equalsIgnoreCase("float")) {
                                    defineItems(serializableParameter, allowableValues, new Oas20Items(),
                                                Float.class);
                                }
                                if (param.getArrayType().equalsIgnoreCase("double")) {
                                    defineItems(serializableParameter, allowableValues, new Oas20Items(),
                                                Double.class);
                                }
                                if (param.getArrayType().equalsIgnoreCase("boolean")) {
                                    defineItems(serializableParameter, allowableValues, new Oas20Items(),
                                                Boolean.class);
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
                        serializableParameter.default_ = param.getDefaultValue();
                    }
                    // add examples
                    if (param.getExamples() != null && param.getExamples().size() >= 1) {
                        // we can only set one example on the parameter
                        Extension exampleExtension = serializableParameter.createExtension();
                        boolean emptyKey = param.getExamples().get(0).getKey().length() == 0;
                        if (emptyKey) {
                            exampleExtension.name = "x-example";
                            exampleExtension.value = param.getExamples().get(0).getValue();
                            serializableParameter.addExtension("x-example", exampleExtension);
                        } else {
                            Map<String, String> exampleValue = new HashMap<String, String>();
                            exampleValue.put(param.getExamples().get(0).getKey(),
                                             param.getExamples().get(0).getValue());
                            exampleExtension.name = "x-examples";
                            exampleExtension.value = exampleValue;
                            serializableParameter.addExtension("x-examples", exampleExtension);
                        }
                    }

                }

                // set schema on body parameter
                if (parameter.in.equals("body")) {
                    Oas20Parameter bp = (Oas20Parameter)parameter;

                    String type = param.getDataType() != null ? param.getDataType() : verb.getType();
                    if (type != null) {
                        if (type.endsWith("[]")) {
                            type = type.substring(0, type.length() - 2);
                            OasSchema arrayModel = (Oas20Schema)bp.createSchema();
                            arrayModel = modelTypeAsProperty(type, openApi, arrayModel);
                            bp.schema = arrayModel;
                        } else {
                            String ref = modelTypeAsRef(type, openApi);
                            if (ref != null) {
                                Oas20Schema refModel = (Oas20Schema)bp.createSchema();
                                refModel.$ref = "#/definitions/" + ref;
                                bp.schema = refModel;
                            } else {
                                OasSchema model = (Oas20Schema)bp.createSchema();
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
                            exampleExtension.value = param.getExamples().get(0).getValue();
                            bp.addExtension("x-example", exampleExtension);
                        } else {
                            Map<String, String> exampleValue = new HashMap<String, String>();
                            exampleValue.put(param.getExamples().get(0).getKey(),
                                             param.getExamples().get(0).getValue());
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
            Oas20Response response = (Oas20Response)op.responses.createResponse("200");
            OasSchema model = response.createSchema();
            model = modelTypeAsProperty(verb.getOutType(), openApi, model);

            response.schema = (Oas20Schema)model;
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

    private static void defineItems(final Oas20Parameter serializableParameter,
                                    final List<String> allowableValues, final Oas20Items items,
                                    final Class<?> type) {
        serializableParameter.items = items;
        if (allowableValues != null && !allowableValues.isEmpty()) {
            if (String.class.equals(type)) {
                items.enum_ = allowableValues;
            } else {
                convertAndSetItemsEnum(items, allowableValues, type);
            }
        }
    }
    
    private static void defineSchemas(final Oas30Parameter serializableParameter,
                                    final List<String> allowableValues, 
                                    final Class<?> type) {
        
        if (allowableValues != null && !allowableValues.isEmpty()) {
            if (String.class.equals(type)) {
                ((Oas30Schema)serializableParameter.schema).enum_ = allowableValues;
            } else {
                convertAndSetItemsEnum(serializableParameter.schema, allowableValues, type);
            }
        }
    }

    private static void convertAndSetItemsEnum(final ExtensibleNode items, final List<String> allowableValues,
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
                        throw (RuntimeException)e;
                    }

                    throw new IllegalStateException(e);
                }
            }).collect(Collectors.toList());
            setEnum.invoke(values);
        } catch (Throwable e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException)e;
            }

            throw new IllegalStateException(e);
        }
    }

    private void doParseResponseMessages(OasDocument openApi, VerbDefinition verb, OasOperation op, String produces) {
        if (op.responses == null) {
            op.responses = op.createResponses();
        }
        for (RestOperationResponseMsgDefinition msg : verb.getResponseMsgs()) {
            if (openApi instanceof Oas20Document) {
                doParseResponseOas20((Oas20Document)openApi, (Oas20Operation)op, msg);
            } else if (openApi instanceof Oas30Document) {
                doParseResponseOas30((Oas30Document)openApi, (Oas30Operation)op, produces, msg);
            }
        }

        // must include an empty noop response if none exists
        if (op.responses == null || op.responses.getResponses().isEmpty()) {
            op.responses.addResponse("200", op.responses.createResponse("200"));
        }
    }

    private void doParseResponseOas30(Oas30Document openApi, Oas30Operation op, String produces,
                                      RestOperationResponseMsgDefinition msg) {
        Oas30Response response = null;

        if (op.responses != null && op.responses.getResponses() != null) {
            response = (Oas30Response)op.responses.getResponse(msg.getCode());
        }
        if (response == null) {
            response = (Oas30Response)op.responses.createResponse(msg.getCode());
            op.responses.addResponse(msg.getCode(), response);
        }
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(msg.getResponseModel())) {
            String[] parts = null;
            if (produces != null) {
                parts = produces.split(",");
                for (String produce : parts) {
                    Oas30MediaType contentType = response.createMediaType(produce);
                    response.addMediaType(produce, contentType);
                    OasSchema model = contentType.createSchema();
                    model = modelTypeAsProperty(msg.getResponseModel(), openApi, model);
                    contentType.schema = (Oas30Schema)model;
                }
            }
        }
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(msg.getMessage())) {
            response.description = msg.getMessage();
        }

        // add headers
        if (msg.getHeaders() != null) {
            for (RestOperationResponseHeaderDefinition header : msg.getHeaders()) {
                String name = header.getName();
                String type = header.getDataType();
                String format = header.getDataFormat();
                
                if ("string".equals(type) || "long".equals(type) || "float".equals(type)
                    || "double".equals(type) || "boolean".equals(type)) {
                    setResponseHeaderOas30(response, header, name, format, type);
                } else if ("int".equals(type) || "integer".equals(type)) {
                    setResponseHeaderOas30(response, header, name, format, "integer");
                } else if ("array".equals(type)) {
                    Oas30Header ap = response.createHeader(name);

                    if (org.apache.camel.util.ObjectHelper.isNotEmpty(header.getDescription())) {
                        ap.description = header.getDescription();
                    }
                    if (header.getArrayType() != null) {
                        String arrayType = header.getArrayType();
                        if (arrayType.equalsIgnoreCase("string")
                            || arrayType.equalsIgnoreCase("long")
                            || arrayType.equalsIgnoreCase("float")
                            || arrayType.equalsIgnoreCase("double")
                            || arrayType.equalsIgnoreCase("boolean")) {
                            setHeaderSchemaOas30(ap, arrayType);
                        } else if (header.getArrayType().equalsIgnoreCase("int")
                            || header.getArrayType().equalsIgnoreCase("integer")) {
                            setHeaderSchemaOas30(ap, "integer");
                        }
                        
                    }
                    // add example
                    if (header.getExample() != null) {
                        Extension exampleExtension = ap.createExtension();
                        exampleExtension.name = "x-example";
                        exampleExtension.value = header.getExample();
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
            Map<String, String> examplesValue = new HashMap<String, String>();
            for (RestPropertyDefinition prop : msg.getExamples()) {
                examplesValue.put(prop.getKey(), prop.getValue());

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

    private void setResponseHeaderOas30(Oas30Response response, RestOperationResponseHeaderDefinition header,
                                        String name, String format, String type) {
        Oas30Header ip = response.createHeader(name);
        response.addHeader(name, ip);
        Oas30Schema schema = ip.createSchema();
        ip.schema = schema;
        schema.type = type;
        if (format != null) {
            schema.format = format;
        }
        ip.description = header.getDescription();

        List<String> values;
        if (!header.getAllowableValues().isEmpty()) {
            values = new ArrayList<>();
            for (String text : header.getAllowableValues()) {
                values.add(text);
            }
            schema.enum_ = values;
        }
        // add example
        if (header.getExample() != null) {
            Extension exampleExtension = ip.createExtension();
            exampleExtension.name = "x-example";
            exampleExtension.value = header.getExample();
            ip.getExtensions().add(exampleExtension);
        }
    }

    private void doParseResponseOas20(Oas20Document openApi, Oas20Operation op,
                                      RestOperationResponseMsgDefinition msg) {
        Oas20Response response = null;

        if (op.responses != null && op.responses.getResponses() != null) {
            response = (Oas20Response)op.responses.getResponse(msg.getCode());
        }
        if (response == null) {
            response = (Oas20Response)op.responses.createResponse(msg.getCode());
            op.responses.addResponse(msg.getCode(), response);
        }
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(msg.getResponseModel())) {
            OasSchema model = response.createSchema();
            model = modelTypeAsProperty(msg.getResponseModel(), openApi, model);

            response.schema = (Oas20Schema)model;
        }
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(msg.getMessage())) {
            response.description = msg.getMessage();
        }

        // add headers
        if (msg.getHeaders() != null) {
            for (RestOperationResponseHeaderDefinition header : msg.getHeaders()) {
                String name = header.getName();
                String type = header.getDataType();
                String format = header.getDataFormat();
                if (response.headers == null) {
                    response.headers = response.createHeaders();
                }
                if ("string".equals(type) || "long".equals(type) || "float".equals(type)
                    || "double".equals(type) || "boolean".equals(type)) {
                    setResponseHeaderOas20(response, header, name, format, type);
                } else if ("int".equals(type) || "integer".equals(type)) {
                    setResponseHeaderOas20(response, header, name, format, "integer");
                } else if ("array".equals(type)) {
                    Oas20Header ap = response.headers.createHeader(name);

                    if (org.apache.camel.util.ObjectHelper.isNotEmpty(header.getDescription())) {
                        ap.description = header.getDescription();
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
                        exampleExtension.value = header.getExample();
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
            Map<String, String> examplesValue = new HashMap<String, String>();
            for (RestPropertyDefinition prop : msg.getExamples()) {
                examplesValue.put(prop.getKey(), prop.getValue());

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

    private void setResponseHeaderOas20(Oas20Response response, RestOperationResponseHeaderDefinition header,
                                        String name, String format, String type) {
        Oas20Header ip = response.headers.createHeader(name);
        ip.type = type;
        if (format != null) {
            ip.format = format;
        }
        ip.description = header.getDescription();

        List<String> values;
        if (!header.getAllowableValues().isEmpty()) {
            values = new ArrayList<>();
            for (String text : header.getAllowableValues()) {
                values.add(text);
            }
            ip.enum_ = values;
        }
        // add example
        if (header.getExample() != null) {
            Extension exampleExtension = ip.createExtension();
            exampleExtension.name = "x-example";
            exampleExtension.value = header.getExample();
            ip.getExtensions().add(exampleExtension);
        }
        response.headers.addHeader(name, ip);
    }

    private OasSchema asModel(String typeName, OasDocument openApi) {
        if (openApi instanceof Oas20Document) {
            boolean array = typeName.endsWith("[]");
            if (array) {
                typeName = typeName.substring(0, typeName.length() - 2);
            }

            if (((Oas20Document)openApi).definitions != null) {
                for (Oas20SchemaDefinition model : ((Oas20Document)openApi).definitions.getDefinitions()) {
                    @SuppressWarnings("rawtypes")
                    Map modelType = (Map)model.getExtension("x-className").value;

                    if (modelType != null && typeName.equals(modelType.get("format"))) {
                        return model;
                    }
                }
            }
        } else if (openApi instanceof Oas30Document) {
            boolean array = typeName.endsWith("[]");
            if (array) {
                typeName = typeName.substring(0, typeName.length() - 2);
            }

            if (((Oas30Document)openApi).components != null
                && ((Oas30Document)openApi).components.schemas != null) {
                for (Oas30SchemaDefinition model : ((Oas30Document)openApi).components.schemas.values()) {
                    @SuppressWarnings("rawtypes")
                    Map modelType = (Map)model.getExtension("x-className").value;

                    if (modelType != null && typeName.equals(modelType.get("format"))) {
                        return model;
                    }
                }
            }
        }
        return null;
    }

    private String modelTypeAsRef(String typeName, OasDocument openApi) {
        boolean array = typeName.endsWith("[]");
        if (array) {
            typeName = typeName.substring(0, typeName.length() - 2);
        }

        OasSchema model = asModel(typeName, openApi);
        if (model != null) {
            typeName = model.type;
            return typeName;
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
                prop.$ref = "#/definitions/" + ref;
            } else if (openApi instanceof Oas30Document) {
                prop.$ref = "#/components/schemas/" + ref;
            }
        } else {
            // special for byte arrays
            if (array && ("byte".equals(typeName) || "java.lang.Byte".equals(typeName))) {
                prop.format = "byte";
                prop.type = "number";
                array = false;
            } else if ("string".equalsIgnoreCase(typeName) || "java.lang.String".equals(typeName)) {
                prop.format = "string";
                prop.type = "sting";
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
     * If the class is annotated with openApi annotations its parsed into a OpenApi model representation which
     * is added to openApi
     *
     * @param clazz the class such as pojo with openApi annotation
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
                if (((Oas20Document)openApi).definitions != null && ((Oas20Document)openApi).definitions
                    .getDefinition(((Oas20SchemaDefinition)entry).getName()) != null) {
                    Oas20SchemaDefinition oldModel = ((Oas20Document)openApi).definitions
                        .getDefinition(((Oas20SchemaDefinition)entry).getName());
                    if (oldModel.getExtensions() != null && !oldModel.getExtensions().isEmpty()) {
                        oldExt = oldModel.getExtension("x-className") != null;
                    }
                }

                if (!oldExt) {
                    ((Oas20Document)openApi).definitions
                        .addDefinition(((Oas20SchemaDefinition)entry).getName(),
                                       (Oas20SchemaDefinition)entry);
                }
            } else if (openApi instanceof Oas30Document) {
                boolean oldExt = false;
                if (((Oas30Document)openApi).components != null && ((Oas30Document)openApi).components
                    .getSchemaDefinition(((Oas30SchemaDefinition)entry).getName()) != null) {
                    Oas30SchemaDefinition oldModel = ((Oas30Document)openApi).components
                        .getSchemaDefinition(((Oas30SchemaDefinition)entry).getName());
                    if (oldModel.getExtensions() != null && !oldModel.getExtensions().isEmpty()) {
                        oldExt = oldModel.getExtension("x-className") != null;
                    }
                }

                if (!oldExt) {
                    ((Oas30Document)openApi).components
                        .addSchemaDefinition(((Oas30SchemaDefinition)entry).getName(),
                                             (Oas30SchemaDefinition)entry);
                }
            }
        }

    }

    /**
     * To sort the rest operations
     */
    private static class VerbOrdering implements Comparator<VerbDefinition> {

        @Override
        public int compare(VerbDefinition a, VerbDefinition b) {

            String u1 = "";
            if (a.getUri() != null) {
                // replace { with _ which comes before a when soring by char
                u1 = a.getUri().replace("{", "_");
            }
            String u2 = "";
            if (b.getUri() != null) {
                // replace { with _ which comes before a when soring by char
                u2 = b.getUri().replace("{", "_");
            }

            int num = u1.compareTo(u2);
            if (num == 0) {
                // same uri, so use http method as sorting
                num = a.asVerb().compareTo(b.asVerb());
            }
            return num;
        }
    }

}
