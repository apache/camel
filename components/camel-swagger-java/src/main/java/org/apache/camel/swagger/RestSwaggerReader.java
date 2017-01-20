/**
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;

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
     */
    public Swagger read(List<RestDefinition> rests, String route, BeanConfig config, String camelContextId, ClassResolver classResolver) {
        Swagger swagger = new Swagger();

        for (RestDefinition rest : rests) {

            if (ObjectHelper.isNotEmpty(route) && !route.equals("/")) {
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

    private void parse(Swagger swagger, RestDefinition rest, String camelContextId, ClassResolver classResolver) {
        List<VerbDefinition> verbs = new ArrayList<>(rest.getVerbs());
        // must sort the verbs by uri so we group them together when an uri has multiple operations
        Collections.sort(verbs, new VerbOrdering());

        // we need to group the operations within the same tag, so use the path as default if not configured
        String pathAsTag = rest.getTag() != null ? rest.getTag() : FileUtil.stripLeadingSeparator(rest.getPath());
        String summary = rest.getDescriptionText();

        if (ObjectHelper.isNotEmpty(pathAsTag)) {
            // add rest as tag
            Tag tag = new Tag();
            tag.description(summary);
            tag.name(pathAsTag);
            swagger.addTag(tag);
        }

        // gather all types in use
        Set<String> types = new LinkedHashSet<>();
        for (VerbDefinition verb : verbs) {

            // check if the Verb Definition must be excluded from documentation
            Boolean apiDocs;
            if (verb.getApiDocs() != null) {
                apiDocs = verb.getApiDocs();
            } else {
                // fallback to option on rest
                apiDocs = rest.getApiDocs();
            }
            if (apiDocs != null && !apiDocs) {
                continue;
            }

            String type = verb.getType();
            if (ObjectHelper.isNotEmpty(type)) {
                if (type.endsWith("[]")) {
                    type = type.substring(0, type.length() - 2);
                }
                types.add(type);
            }
            type = verb.getOutType();
            if (ObjectHelper.isNotEmpty(type)) {
                if (type.endsWith("[]")) {
                    type = type.substring(0, type.length() - 2);
                }
                types.add(type);
            }
            // there can also be types in response messages
            if (verb.getResponseMsgs() != null) {
                for (RestOperationResponseMsgDefinition def : verb.getResponseMsgs()) {
                    type = def.getResponseModel();
                    if (ObjectHelper.isNotEmpty(type)) {
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
            Class<?> clazz = classResolver.resolveClass(type);
            appendModels(clazz, swagger);
        }

        doParseVerbs(swagger, rest, camelContextId, verbs, pathAsTag);
    }

    private void doParseVerbs(Swagger swagger, RestDefinition rest, String camelContextId, List<VerbDefinition> verbs, String pathAsTag) {
        // used during gathering of apis
        List<Path> paths = new ArrayList<>();

        String basePath = rest.getPath();

        for (VerbDefinition verb : verbs) {
            // check if the Verb Definition must be excluded from documentation
            Boolean apiDocs;
            if (verb.getApiDocs() != null) {
                apiDocs = verb.getApiDocs();
            } else {
                // fallback to option on rest
                apiDocs = rest.getApiDocs();
            }
            if (apiDocs != null && !apiDocs) {
                continue;
            }
            // the method must be in lower case
            String method = verb.asVerb().toLowerCase(Locale.US);
            // operation path is a key
            String opPath = SwaggerHelper.buildUrl(basePath, verb.getUri());

            Operation op = new Operation();
            if (ObjectHelper.isNotEmpty(pathAsTag)) {
                // group in the same tag
                op.addTag(pathAsTag);
            }

            // add id as vendor extensions
            op.getVendorExtensions().put("x-camelContextId", camelContextId);
            op.getVendorExtensions().put("x-routeId", verb.getRouteId());

            Path path = swagger.getPath(opPath);
            if (path == null) {
                path = new Path();
                paths.add(path);
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
                    parameter.setDescription(param.getDescription());
                    parameter.setRequired(param.getRequired());

                    // set type on parameter
                    if (parameter instanceof SerializableParameter) {
                        SerializableParameter serializableParameter = (SerializableParameter) parameter;

                        if (param.getDataType() != null) {
                            serializableParameter.setType(param.getDataType());
                            if (param.getDataType().equalsIgnoreCase("array")) {
                                if (param.getArrayType() != null) {
                                    if (param.getArrayType().equalsIgnoreCase("string")) {
                                        serializableParameter.setItems(new StringProperty());
                                    }
                                    if (param.getArrayType().equalsIgnoreCase("int") || param.getArrayType().equalsIgnoreCase("integer")) {
                                        serializableParameter.setItems(new IntegerProperty());
                                    }
                                    if (param.getArrayType().equalsIgnoreCase("long")) {
                                        serializableParameter.setItems(new LongProperty());
                                    }
                                    if (param.getArrayType().equalsIgnoreCase("float")) {
                                        serializableParameter.setItems(new FloatProperty());
                                    }
                                    if (param.getArrayType().equalsIgnoreCase("double")) {
                                        serializableParameter.setItems(new DoubleProperty());
                                    }
                                    if (param.getArrayType().equalsIgnoreCase("boolean")) {
                                        serializableParameter.setItems(new BooleanProperty());
                                    }
                                }
                            }
                        }
                        if (param.getCollectionFormat() != null) {
                            serializableParameter.setCollectionFormat(param.getCollectionFormat().name());
                        }
                        if (param.getAllowableValues() != null && !param.getAllowableValues().isEmpty()) {
                            serializableParameter.setEnum(param.getAllowableValues());
                        }
                    }

                    // set default value on parameter
                    if (parameter instanceof AbstractSerializableParameter) {
                        AbstractSerializableParameter qp = (AbstractSerializableParameter) parameter;
                        if (param.getDefaultValue() != null) {
                            qp.setDefaultValue(param.getDefaultValue());
                        }
                    }

                    // set schema on body parameter
                    if (parameter instanceof BodyParameter) {
                        BodyParameter bp = (BodyParameter) parameter;

                        if (verb.getType() != null) {
                            if (verb.getType().endsWith("[]")) {
                                String typeName = verb.getType();
                                typeName = typeName.substring(0, typeName.length() - 2);
                                Property prop = modelTypeAsProperty(typeName, swagger);
                                if (prop != null) {
                                    ArrayModel arrayModel = new ArrayModel();
                                    arrayModel.setItems(prop);
                                    bp.setSchema(arrayModel);
                                }
                            } else {
                                String ref = modelTypeAsRef(verb.getType(), swagger);
                                if (ref != null) {
                                    bp.setSchema(new RefModel(ref));
                                }
                            }
                        }
                    }

                    op.addParameter(parameter);
                }
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

    private void doParseResponseMessages(Swagger swagger, VerbDefinition verb, Operation op) {
        for (RestOperationResponseMsgDefinition msg : verb.getResponseMsgs()) {
            Response response = null;
            if (op.getResponses() != null) {
                response = op.getResponses().get(msg.getCode());
            }
            if (response == null) {
                response = new Response();
            }
            if (ObjectHelper.isNotEmpty(msg.getResponseModel())) {
                Property prop = modelTypeAsProperty(msg.getResponseModel(), swagger);
                response.setSchema(prop);
            }
            response.setDescription(msg.getMessage());

            // add headers
            if (msg.getHeaders() != null) {
                for (RestOperationResponseHeaderDefinition header : msg.getHeaders()) {
                    String name = header.getName();
                    String type = header.getDataType();
                    if ("string".equals(type)) {
                        StringProperty sp = new StringProperty();
                        sp.setName(name);
                        sp.setDescription(header.getDescription());
                        if (header.getAllowableValues() != null) {
                            sp.setEnum(header.getAllowableValues());
                        }
                        response.addHeader(name, sp);
                    } else if ("int".equals(type) || "integer".equals(type)) {
                        IntegerProperty ip = new IntegerProperty();
                        ip.setName(name);
                        ip.setDescription(header.getDescription());

                        List<Integer> values;
                        if (!header.getAllowableValues().isEmpty()) {
                            values = new ArrayList<Integer>();
                            for (String text : header.getAllowableValues()) {
                                values.add(Integer.valueOf(text));
                            }
                            ip.setEnum(values);
                        }
                        response.addHeader(name, ip);
                    } else if ("long".equals(type)) {
                        LongProperty lp = new LongProperty();
                        lp.setName(name);
                        lp.setDescription(header.getDescription());

                        List<Long> values;
                        if (!header.getAllowableValues().isEmpty()) {
                            values = new ArrayList<Long>();
                            for (String text : header.getAllowableValues()) {
                                values.add(Long.valueOf(text));
                            }
                            lp.setEnum(values);
                        }
                        response.addHeader(name, lp);
                    } else if ("float".equals(type)) {
                        FloatProperty lp = new FloatProperty();
                        lp.setName(name);
                        lp.setDescription(header.getDescription());

                        List<Float> values;
                        if (!header.getAllowableValues().isEmpty()) {
                            values = new ArrayList<Float>();
                            for (String text : header.getAllowableValues()) {
                                values.add(Float.valueOf(text));
                            }
                            lp.setEnum(values);
                        }
                        response.addHeader(name, lp);
                    } else if ("double".equals(type)) {
                        DoubleProperty dp = new DoubleProperty();
                        dp.setName(name);
                        dp.setDescription(header.getDescription());

                        List<Double> values;
                        if (!header.getAllowableValues().isEmpty()) {
                            values = new ArrayList<Double>();
                            for (String text : header.getAllowableValues()) {
                                values.add(Double.valueOf(text));
                            }
                            dp.setEnum(values);
                        }
                        response.addHeader(name, dp);
                    } else if ("boolean".equals(type)) {
                        BooleanProperty bp = new BooleanProperty();
                        bp.setName(name);
                        bp.setDescription(header.getDescription());
                        response.addHeader(name, bp);
                    } else if ("array".equals(type)) {
                        ArrayProperty ap = new ArrayProperty();
                        ap.setName(name);
                        ap.setDescription(header.getDescription());
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
                        response.addHeader(name, ap);
                    }
                }
            }

            op.addResponse(msg.getCode(), response);
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

        Property prop = ref != null ? new RefProperty(ref) : new StringProperty(typeName);
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
