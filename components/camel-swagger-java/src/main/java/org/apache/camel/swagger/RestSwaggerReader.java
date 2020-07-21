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
package org.apache.camel.swagger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.models.ArrayModel;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.RefModel;
import io.swagger.models.Response;
import io.swagger.models.Swagger;
import io.swagger.models.Tag;
import io.swagger.models.auth.ApiKeyAuthDefinition;
import io.swagger.models.auth.BasicAuthDefinition;
import io.swagger.models.auth.In;
import io.swagger.models.auth.OAuth2Definition;
import io.swagger.models.parameters.AbstractSerializableParameter;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.FormParameter;
import io.swagger.models.parameters.HeaderParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.PathParameter;
import io.swagger.models.parameters.QueryParameter;
import io.swagger.models.parameters.SerializableParameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.BooleanProperty;
import io.swagger.models.properties.ByteArrayProperty;
import io.swagger.models.properties.DoubleProperty;
import io.swagger.models.properties.FloatProperty;
import io.swagger.models.properties.IntegerProperty;
import io.swagger.models.properties.LongProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;
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
 * A Camel REST-DSL swagger reader that parse the rest-dsl into a swagger model representation.
 * <p/>
 * This reader supports the <a href="http://swagger.io/specification/">Swagger Specification 2.0</a>
 */
public class RestSwaggerReader {

    /**
     * Read the REST-DSL definition's and parse that as a Swagger model representation
     *
     * @param rests             the rest-dsl
     * @param route             optional route path to filter the rest-dsl to only include from the chose route
     * @param config            the swagger configuration
     * @param classResolver     class resolver to use
     * @return the swagger model
     * @throws ClassNotFoundException 
     */
    public Swagger read(List<RestDefinition> rests, String route, BeanConfig config, String camelContextId, ClassResolver classResolver) throws ClassNotFoundException {
        Swagger swagger = new Swagger();

        for (RestDefinition rest : rests) {

            if (org.apache.camel.util.ObjectHelper.isNotEmpty(route) && !route.equals("/")) {
                // filter by route
                if (!rest.getPath().equals(route)) {
                    continue;
                }
            }

            parse(swagger, rest, camelContextId, classResolver);
        }

        // configure before returning
        swagger = config.configure(swagger);
        return swagger;
    }

    private void parse(Swagger swagger, RestDefinition rest, String camelContextId, ClassResolver classResolver) throws ClassNotFoundException {
        List<VerbDefinition> verbs = new ArrayList<>(rest.getVerbs());
        // must sort the verbs by uri so we group them together when an uri has multiple operations
        verbs.sort(new VerbOrdering());

        // we need to group the operations within the same tag, so use the path as default if not configured
        String pathAsTag = rest.getTag() != null ? rest.getTag() : FileUtil.stripLeadingSeparator(rest.getPath());
        String summary = rest.getDescriptionText();

        if (org.apache.camel.util.ObjectHelper.isNotEmpty(pathAsTag)) {
            // add rest as tag
            Tag tag = new Tag();
            tag.description(summary);
            tag.name(pathAsTag);
            swagger.addTag(tag);
        }

        // setup security definitions
        RestSecuritiesDefinition sd = rest.getSecurityDefinitions();
        if (sd != null) {
            for (RestSecurityDefinition def : sd.getSecurityDefinitions()) {
                if (def instanceof RestSecurityBasicAuth) {
                    BasicAuthDefinition auth = new BasicAuthDefinition();
                    auth.setDescription(def.getDescription());
                    swagger.addSecurityDefinition(def.getKey(), auth);
                } else if (def instanceof RestSecurityApiKey) {
                    RestSecurityApiKey rs = (RestSecurityApiKey) def;
                    ApiKeyAuthDefinition auth = new ApiKeyAuthDefinition();
                    auth.setDescription(rs.getDescription());
                    auth.setName(rs.getName());
                    if (rs.getInHeader() != null && Boolean.parseBoolean(rs.getInHeader())) {
                        auth.setIn(In.HEADER);
                    } else {
                        auth.setIn(In.QUERY);
                    }
                    swagger.addSecurityDefinition(def.getKey(), auth);
                } else if (def instanceof RestSecurityOAuth2) {
                    RestSecurityOAuth2 rs = (RestSecurityOAuth2) def;
                    OAuth2Definition auth = new OAuth2Definition();
                    auth.setDescription(rs.getDescription());
                    String flow = rs.getFlow();
                    if (flow == null) {
                        if (rs.getAuthorizationUrl() != null && rs.getTokenUrl() != null) {
                            flow = "accessCode";
                        } else if (rs.getTokenUrl() == null && rs.getAuthorizationUrl() != null) {
                            flow = "implicit";
                        }
                    }
                    auth.setFlow(flow);
                    auth.setAuthorizationUrl(rs.getAuthorizationUrl());
                    auth.setTokenUrl(rs.getTokenUrl());
                    for (RestPropertyDefinition scope : rs.getScopes()) {
                        auth.addScope(scope.getKey(), scope.getValue());
                    }
                    swagger.addSecurityDefinition(def.getKey(), auth);
                }
            }
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
            appendModels(clazz, swagger);
        }

        doParseVerbs(swagger, rest, camelContextId, verbs, pathAsTag);
    }

    private void doParseVerbs(Swagger swagger, RestDefinition rest, String camelContextId, List<VerbDefinition> verbs, String pathAsTag) {
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
            String opPath = SwaggerHelper.buildUrl(basePath, verb.getUri());

            Operation op = new Operation();
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(pathAsTag)) {
                // group in the same tag
                op.addTag(pathAsTag);
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
            op.operationId(operationId);

            // add id as vendor extensions
            op.getVendorExtensions().put("x-camelContextId", camelContextId);
            op.getVendorExtensions().put("x-routeId", routeId);

            Path path = swagger.getPath(opPath);
            if (path == null) {
                path = new Path();
            }
            path = path.set(method, op);

            String consumes = verb.getConsumes() != null ? verb.getConsumes() : rest.getConsumes();
            if (consumes != null) {
                String[] parts = consumes.split(",");
                for (String part : parts) {
                    op.addConsumes(part);
                }
            }

            String produces = verb.getProduces() != null ? verb.getProduces() : rest.getProduces();
            if (produces != null) {
                String[] parts = produces.split(",");
                for (String part : parts) {
                    op.addProduces(part);
                }
            }

            if (verb.getDescriptionText() != null) {
                op.summary(verb.getDescriptionText());
            }

            // security
            for (SecurityDefinition sd : verb.getSecurity()) {
                List<String> scopes = new ArrayList<>();
                if (sd.getScopes() != null) {
                    for (String scope : ObjectHelper.createIterable(sd.getScopes())) {
                        scopes.add(scope);
                    }
                }
                op.addSecurity(sd.getKey(), scopes);
            }

            for (RestOperationParamDefinition param : verb.getParams()) {
                Parameter parameter = null;
                if (param.getType().equals(RestParamType.body)) {
                    parameter = new BodyParameter();
                } else if (param.getType().equals(RestParamType.formData)) {
                    parameter = new FormParameter();
                } else if (param.getType().equals(RestParamType.header)) {
                    parameter = new HeaderParameter();
                } else if (param.getType().equals(RestParamType.path)) {
                    parameter = new PathParameter();
                } else if (param.getType().equals(RestParamType.query)) {
                    parameter = new QueryParameter();
                }

                if (parameter != null) {
                    parameter.setName(param.getName());
                    if (org.apache.camel.util.ObjectHelper.isNotEmpty(param.getDescription())) {
                        parameter.setDescription(param.getDescription());
                    }
                    parameter.setRequired(param.getRequired());

                    // set type on parameter
                    if (parameter instanceof SerializableParameter) {
                        SerializableParameter serializableParameter = (SerializableParameter) parameter;

                        final boolean isArray = param.getDataType().equalsIgnoreCase("array");
                        final List<String> allowableValues = param.getAllowableValues();
                        final boolean hasAllowableValues = allowableValues != null && !allowableValues.isEmpty();
                        if (param.getDataType() != null) {
                            serializableParameter.setType(param.getDataType());
                            if (param.getDataFormat() != null) {
                                serializableParameter.setFormat(param.getDataFormat());
                            }
                            if (isArray) {
                                if (param.getArrayType() != null) {
                                    if (param.getArrayType().equalsIgnoreCase("string")) {
                                        defineItems(serializableParameter, allowableValues, new StringProperty(), String.class);
                                    }
                                    if (param.getArrayType().equalsIgnoreCase("int") || param.getArrayType().equalsIgnoreCase("integer")) {
                                        defineItems(serializableParameter, allowableValues, new IntegerProperty(), Integer.class);
                                    }
                                    if (param.getArrayType().equalsIgnoreCase("long")) {
                                        defineItems(serializableParameter, allowableValues, new LongProperty(), Long.class);
                                    }
                                    if (param.getArrayType().equalsIgnoreCase("float")) {
                                        defineItems(serializableParameter, allowableValues, new FloatProperty(), Float.class);
                                    }
                                    if (param.getArrayType().equalsIgnoreCase("double")) {
                                        defineItems(serializableParameter, allowableValues, new DoubleProperty(), Double.class);
                                    }
                                    if (param.getArrayType().equalsIgnoreCase("boolean")) {
                                        defineItems(serializableParameter, allowableValues, new BooleanProperty(), Boolean.class);
                                    }
                                }
                            }
                        }
                        if (param.getCollectionFormat() != null) {
                            serializableParameter.setCollectionFormat(param.getCollectionFormat().name());
                        }
                        if (hasAllowableValues && !isArray) {
                            serializableParameter.setEnum(allowableValues);
                        }
                    }

                    if (parameter instanceof AbstractSerializableParameter) {
                        AbstractSerializableParameter qp = (AbstractSerializableParameter) parameter;
                        // set default value on parameter
                        if (org.apache.camel.util.ObjectHelper.isNotEmpty(param.getDefaultValue())) {
                            qp.setDefaultValue(param.getDefaultValue());
                        }
                        // add examples
                        if (param.getExamples() != null && param.getExamples().size() >= 1) {
                            // we can only set one example on the parameter
                            qp.example(param.getExamples().get(0).getValue());
                        }
                    }

                    // set schema on body parameter
                    if (parameter instanceof BodyParameter) {
                        BodyParameter bp = (BodyParameter) parameter;

                        String type = param.getDataType() != null ? param.getDataType() : verb.getType();
                        if (type != null) {
                            if (type.endsWith("[]")) {
                                type = type.substring(0, type.length() - 2);
                                Property prop = modelTypeAsProperty(type, swagger);
                                if (prop != null) {
                                    ArrayModel arrayModel = new ArrayModel();
                                    arrayModel.setItems(prop);
                                    bp.setSchema(arrayModel);
                                }
                            } else {
                                String ref = modelTypeAsRef(type, swagger);
                                if (ref != null) {
                                    bp.setSchema(new RefModel(ref));
                                } else {
                                    Property prop = modelTypeAsProperty(type, swagger);
                                    if (prop != null) {
                                        ModelImpl model = new ModelImpl();
                                        model.setFormat(prop.getFormat());
                                        model.setType(prop.getType());
                                        bp.setSchema(model);
                                    }
                                }
                            }
                        }
                        // add examples
                        if (param.getExamples() != null) {
                            for (RestPropertyDefinition prop : param.getExamples()) {
                                bp.example(prop.getKey(), prop.getValue());
                            }
                        }
                    }

                    op.addParameter(parameter);
                }
            }

            // clear parameters if its empty
            if (op.getParameters().isEmpty()) {
                op.setParameters(null);
            }

            // if we have an out type then set that as response message
            if (verb.getOutType() != null) {
                Response response = new Response();
                Property prop = modelTypeAsProperty(verb.getOutType(), swagger);
                response.setSchema(prop);
                response.setDescription("Output type");
                op.addResponse("200", response);
            }

            // enrich with configured response messages from the rest-dsl
            doParseResponseMessages(swagger, verb, op);

            // add path
            swagger.path(opPath, path);
        }
    }

    private static void defineItems(final SerializableParameter serializableParameter,
        final List<String> allowableValues, final Property items, final Class<?> type) {
        serializableParameter.setItems(items);
        if (allowableValues != null && !allowableValues.isEmpty()) {
            if (String.class.equals(type)) {
                ((StringProperty) items).setEnum(allowableValues);
            } else {
                convertAndSetItemsEnum(items, allowableValues, type);
            }
        }
    }

    private static void convertAndSetItemsEnum(final Property items, final List<String> allowableValues, final Class<?> type) {
        try {
            final MethodHandle valueOf = publicLookup().findStatic(type, "valueOf", MethodType.methodType(type, String.class));
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

    private void doParseResponseMessages(Swagger swagger, VerbDefinition verb, Operation op) {
        for (RestOperationResponseMsgDefinition msg : verb.getResponseMsgs()) {
            Response response = null;
            if (op.getResponses() != null) {
                response = op.getResponses().get(msg.getCode());
            }
            if (response == null) {
                response = new Response();
            }
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(msg.getResponseModel())) {
                Property prop = modelTypeAsProperty(msg.getResponseModel(), swagger);
                response.setSchema(prop);
            }
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(msg.getMessage())) {
                response.setDescription(msg.getMessage());
            }

            // add headers
            if (msg.getHeaders() != null) {
                for (RestOperationResponseHeaderDefinition header : msg.getHeaders()) {
                    String name = header.getName();
                    String type = header.getDataType();
                    String format = header.getDataFormat();
                    if ("string".equals(type)) {
                        StringProperty sp = new StringProperty();
                        sp.setName(name);
                        if (format != null) {
                            sp.setFormat(format);
                        }
                        sp.setDescription(header.getDescription());
                        if (header.getAllowableValues() != null) {
                            sp.setEnum(header.getAllowableValues());
                        }
                        // add example
                        if (header.getExample() != null) {
                            sp.example(header.getExample());
                        }
                        response.addHeader(name, sp);
                    } else if ("int".equals(type) || "integer".equals(type)) {
                        IntegerProperty ip = new IntegerProperty();
                        ip.setName(name);
                        if (format != null) {
                            ip.setFormat(format);
                        }
                        ip.setDescription(header.getDescription());

                        List<Integer> values;
                        if (!header.getAllowableValues().isEmpty()) {
                            values = new ArrayList<>();
                            for (String text : header.getAllowableValues()) {
                                values.add(Integer.valueOf(text));
                            }
                            ip.setEnum(values);
                        }
                        // add example
                        if (header.getExample() != null) {
                            ip.example(Integer.valueOf(header.getExample()));
                        }
                        response.addHeader(name, ip);
                    } else if ("long".equals(type)) {
                        LongProperty lp = new LongProperty();
                        lp.setName(name);
                        if (format != null) {
                            lp.setFormat(format);
                        }
                        lp.setDescription(header.getDescription());

                        List<Long> values;
                        if (!header.getAllowableValues().isEmpty()) {
                            values = new ArrayList<>();
                            for (String text : header.getAllowableValues()) {
                                values.add(Long.valueOf(text));
                            }
                            lp.setEnum(values);
                        }
                        // add example
                        if (header.getExample() != null) {
                            lp.example(Long.valueOf(header.getExample()));
                        }
                        response.addHeader(name, lp);
                    } else if ("float".equals(type)) {
                        FloatProperty fp = new FloatProperty();
                        fp.setName(name);
                        if (format != null) {
                            fp.setFormat(format);
                        }
                        fp.setDescription(header.getDescription());

                        List<Float> values;
                        if (!header.getAllowableValues().isEmpty()) {
                            values = new ArrayList<>();
                            for (String text : header.getAllowableValues()) {
                                values.add(Float.valueOf(text));
                            }
                            fp.setEnum(values);
                        }
                        // add example
                        if (header.getExample() != null) {
                            fp.example(Float.valueOf(header.getExample()));
                        }
                        response.addHeader(name, fp);
                    } else if ("double".equals(type)) {
                        DoubleProperty dp = new DoubleProperty();
                        dp.setName(name);
                        if (format != null) {
                            dp.setFormat(format);
                        }
                        dp.setDescription(header.getDescription());

                        List<Double> values;
                        if (!header.getAllowableValues().isEmpty()) {
                            values = new ArrayList<>();
                            for (String text : header.getAllowableValues()) {
                                values.add(Double.valueOf(text));
                            }
                            dp.setEnum(values);
                        }
                        // add example
                        if (header.getExample() != null) {
                            dp.example(Double.valueOf(header.getExample()));
                        }
                        response.addHeader(name, dp);
                    } else if ("boolean".equals(type)) {
                        BooleanProperty bp = new BooleanProperty();
                        bp.setName(name);
                        if (format != null) {
                            bp.setFormat(format);
                        }
                        bp.setDescription(header.getDescription());
                        // add example
                        if (header.getExample() != null) {
                            bp.example(Boolean.valueOf(header.getExample()));
                        }
                        response.addHeader(name, bp);
                    } else if ("array".equals(type)) {
                        ArrayProperty ap = new ArrayProperty();
                        ap.setName(name);
                        if (org.apache.camel.util.ObjectHelper.isNotEmpty(header.getDescription())) {
                            ap.setDescription(header.getDescription());
                        }
                        if (header.getArrayType() != null) {
                            if (header.getArrayType().equalsIgnoreCase("string")) {
                                ap.setItems(new StringProperty());
                            }
                            if (header.getArrayType().equalsIgnoreCase("int") || header.getArrayType().equalsIgnoreCase("integer")) {
                                ap.setItems(new IntegerProperty());
                            }
                            if (header.getArrayType().equalsIgnoreCase("long")) {
                                ap.setItems(new LongProperty());
                            }
                            if (header.getArrayType().equalsIgnoreCase("float")) {
                                ap.setItems(new FloatProperty());
                            }
                            if (header.getArrayType().equalsIgnoreCase("double")) {
                                ap.setItems(new DoubleProperty());
                            }
                            if (header.getArrayType().equalsIgnoreCase("boolean")) {
                                ap.setItems(new BooleanProperty());
                            }
                        }
                        // add example
                        if (header.getExample() != null) {
                            ap.example(header.getExample());
                        }
                        response.addHeader(name, ap);
                    }
                }
            }

            // add examples
            if (msg.getExamples() != null) {
                for (RestPropertyDefinition prop : msg.getExamples()) {
                    response.example(prop.getKey(), prop.getValue());
                }
            }

            op.addResponse(msg.getCode(), response);
        }

        // must include an empty noop response if none exists
        if (op.getResponses() == null) {
            op.addResponse("200", new Response());
        }
    }

    private Model asModel(String typeName, Swagger swagger) {
        boolean array = typeName.endsWith("[]");
        if (array) {
            typeName = typeName.substring(0, typeName.length() - 2);
        }

        if (swagger.getDefinitions() != null) {
            for (Model model : swagger.getDefinitions().values()) {
                StringProperty modelType = (StringProperty) model.getVendorExtensions().get("x-className");
                if (modelType != null && typeName.equals(modelType.getFormat())) {
                    return model;
                }
            }
        }
        return null;
    }

    private String modelTypeAsRef(String typeName, Swagger swagger) {
        boolean array = typeName.endsWith("[]");
        if (array) {
            typeName = typeName.substring(0, typeName.length() - 2);
        }

        Model model = asModel(typeName, swagger);
        if (model != null) {
            typeName = ((ModelImpl) model).getName();
            return typeName;
        }

        return null;
    }

    private Property modelTypeAsProperty(String typeName, Swagger swagger) {
        boolean array = typeName.endsWith("[]");
        if (array) {
            typeName = typeName.substring(0, typeName.length() - 2);
        }

        String ref = modelTypeAsRef(typeName, swagger);

        Property prop;

        if (ref != null) {
            prop = new RefProperty(ref);
        } else {
            // special for byte arrays
            if (array && ("byte".equals(typeName) || "java.lang.Byte".equals(typeName))) {
                prop = new ByteArrayProperty();
                array = false;
            } else if ("string".equalsIgnoreCase(typeName) || "java.lang.String".equals(typeName)) {
                prop = new StringProperty();
            } else if ("int".equals(typeName) || "java.lang.Integer".equals(typeName)) {
                prop = new IntegerProperty();
            } else if ("long".equals(typeName) || "java.lang.Long".equals(typeName)) {
                prop = new LongProperty();
            } else if ("float".equals(typeName) || "java.lang.Float".equals(typeName)) {
                prop = new FloatProperty();
            } else if ("double".equals(typeName) || "java.lang.Double".equals(typeName)) {
                prop = new DoubleProperty();
            } else if ("boolean".equals(typeName) || "java.lang.Boolean".equals(typeName)) {
                prop = new BooleanProperty();
            } else {
                prop = new StringProperty(typeName);
            }
        }

        if (array) {
            return new ArrayProperty(prop);
        } else {
            return prop;
        }
    }

    /**
     * If the class is annotated with swagger annotations its parsed into a Swagger model representation
     * which is added to swagger
     *
     * @param clazz   the class such as pojo with swagger annotation
     * @param swagger the swagger model
     */
    private void appendModels(Class clazz, Swagger swagger) {
        RestModelConverters converters = new RestModelConverters();
        final Map<String, Model> models = converters.readClass(clazz);
        for (Map.Entry<String, Model> entry : models.entrySet()) {

            // favor keeping any existing model that has the vendor extension in the model
            boolean oldExt = false;
            if (swagger.getDefinitions() != null && swagger.getDefinitions().get(entry.getKey()) != null) {
                Model oldModel = swagger.getDefinitions().get(entry.getKey());
                if (oldModel.getVendorExtensions() != null && !oldModel.getVendorExtensions().isEmpty()) {
                    oldExt = oldModel.getVendorExtensions().get("x-className") != null;
                }
            }

            if (!oldExt) {
                swagger.model(entry.getKey(), entry.getValue());
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
