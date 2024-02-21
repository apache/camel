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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.models.ArrayModel;
import io.swagger.models.Contact;
import io.swagger.models.Info;
import io.swagger.models.License;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.RefModel;
import io.swagger.models.Response;
import io.swagger.models.Scheme;
import io.swagger.models.Swagger;
import io.swagger.models.Tag;
import io.swagger.models.auth.ApiKeyAuthDefinition;
import io.swagger.models.auth.BasicAuthDefinition;
import io.swagger.models.auth.In;
import io.swagger.models.auth.OAuth2Definition;
import io.swagger.models.auth.SecuritySchemeDefinition;
import io.swagger.models.parameters.AbstractSerializableParameter;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.CookieParameter;
import io.swagger.models.parameters.HeaderParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.PathParameter;
import io.swagger.models.parameters.QueryParameter;
import io.swagger.models.properties.IntegerProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.PropertyBuilder;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;
import io.swagger.util.Json;
import io.swagger.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.ByteArraySchema;
import io.swagger.v3.oas.models.media.DateSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.FileSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.PasswordSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter.StyleEnum;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenAPI3to2 {

    private Swagger openApi2;
    private Logger log = LoggerFactory.getLogger(getClass());

    public void convertOpenAPI3to2(OpenAPI openApi3) {
        openApi2 = new Swagger()
                .info(convertInfo(openApi3.getInfo()))
                .tags(getTags(openApi3.getTags()))
                .paths(getPaths(openApi3.getPaths()));
        setServerInfo(openApi2, openApi3.getServers());
        if (openApi3.getComponents() != null && openApi3.getComponents().getSchemas() != null) {
            for (Map.Entry<String, Schema> entry : openApi3.getComponents().getSchemas().entrySet()) {
                openApi2.addDefinition(entry.getKey(), convertSchema(entry.getValue()));
            }
        }
        if (openApi3.getComponents() != null && openApi3.getComponents().getSecuritySchemes() != null) {
            convertSecurityDefinitions(openApi3.getComponents().getSecuritySchemes());
        }
        if (openApi3.getSecurity() != null) {
            for (SecurityRequirement sr : openApi3.getSecurity()) {
                openApi2.addSecurity(convertSecurityRequirement(sr));
            }
        }
    }

    private io.swagger.models.SecurityRequirement convertSecurityRequirement(SecurityRequirement sr) {
        io.swagger.models.SecurityRequirement sreq = new io.swagger.models.SecurityRequirement();
        for (Map.Entry<String, List<String>> reqMap : sr.entrySet()) {
            sreq.requirement(reqMap.getKey(), reqMap.getValue());
        }
        return sreq;
    }

    private void convertSecurityDefinitions(Map<String, SecurityScheme> securitySchemes) {
        for (Map.Entry<String, SecurityScheme> entry : securitySchemes.entrySet()) {
            openApi2.addSecurityDefinition(entry.getKey(), convertSecurityScheme(entry.getValue()));
        }
    }

    private SecuritySchemeDefinition convertSecurityScheme(SecurityScheme securityScheme) {
        SecuritySchemeDefinition swaggerScheme = null;
        switch (securityScheme.getType()) {
            case HTTP:
                if ("basic".equals(securityScheme.getScheme())) {
                    swaggerScheme = new BasicAuthDefinition();
                } else {
                    throw new IllegalStateException("OpenAPI 2.0 does not support bearer token security schemes.");
                }
                break;
            case APIKEY:
                if (securityScheme.getIn() == SecurityScheme.In.COOKIE) {
                    throw new IllegalStateException("Invalid 'in' value for API Key security scheme");
                } else {
                    swaggerScheme
                            = new ApiKeyAuthDefinition(securityScheme.getName(), In.forValue(securityScheme.getIn().name()));
                }
                break;
            case OAUTH2:
                OAuth2Definition oauth2 = new OAuth2Definition();
                OAuthFlows flows = securityScheme.getFlows();
                Scopes scopes = null;
                if (flows.getImplicit() != null) {
                    oauth2.implicit(flows.getImplicit().getAuthorizationUrl());
                    scopes = flows.getImplicit().getScopes();
                } else if (flows.getPassword() != null) {
                    oauth2.password(flows.getPassword().getTokenUrl());
                    scopes = flows.getPassword().getScopes();
                } else if (flows.getClientCredentials() != null) {
                    oauth2.application(flows.getClientCredentials().getTokenUrl());
                    scopes = flows.getClientCredentials().getScopes();
                } else if (flows.getAuthorizationCode() != null) {
                    oauth2.accessCode(flows.getAuthorizationCode().getAuthorizationUrl(),
                            flows.getAuthorizationCode().getTokenUrl());
                    scopes = flows.getAuthorizationCode().getScopes();
                } else {
                    // TODO: handle other
                }
                if (scopes != null) {
                    for (Map.Entry<String, String> entry : scopes.entrySet()) {
                        oauth2.addScope(entry.getKey(), entry.getValue());
                    }
                }

                swaggerScheme = oauth2;
                break;
            default:
                throw new IllegalStateException(
                        "Security scheme " + securityScheme.getType().toString()
                                                + "is not supported in OpenAPI 2");
        }
        if (swaggerScheme != null && securityScheme.getDescription() != null) {
            swaggerScheme.setDescription(securityScheme.getDescription());
        }
        return swaggerScheme;
    }

    private void setServerInfo(Swagger openApi2, List<Server> servers) {
        // set host, basePath & schemas
        for (Server server : servers) {
            String serverUrl = server.getUrl();
            if (serverUrl != null && !serverUrl.isEmpty()) {
                try {
                    URL url = new URL(serverUrl);
                    openApi2.setHost(url.getHost() + ":" + url.getPort());
                    openApi2.setBasePath(url.getPath());
                    Scheme scheme = Scheme.forValue(url.getProtocol());
                    if (scheme != null) {
                        openApi2.addScheme(scheme);
                    }
                } catch (MalformedURLException e) {
                    log.warn("Malformed URL in configuration: {}", serverUrl);
                    int basePathIndex = serverUrl.lastIndexOf('/');
                    if (basePathIndex > 0) {
                        openApi2.setBasePath(serverUrl.substring(basePathIndex));
                    } else {
                        basePathIndex = serverUrl.length() - 1;
                    }
                    int protIndex = serverUrl.indexOf("://");
                    if (protIndex > 0) {
                        String protocol = serverUrl.substring(0, protIndex);
                        Scheme scheme = Scheme.forValue(protocol);
                        if (scheme != null) {
                            openApi2.addScheme(scheme);
                        }
                        openApi2.setHost(serverUrl.substring(protIndex + 3, basePathIndex));
                    } else {
                        openApi2.setHost(serverUrl.substring(0, basePathIndex));
                    }
                }
            }
        }

    }

    public byte[] getSwaggerAsJson() {
        ObjectMapper mapper = Json.mapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        //        mapper.setSerializationInclusion(.Include.NON_NULL);
        try {
            return mapper.writeValueAsBytes(openApi2);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            return new byte[0];
        }
    }

    public byte[] getSwaggerAsYaml() {
        ObjectMapper mapper = Json.mapper();
        try {
            JsonNode node = mapper.readTree(getSwaggerAsJson());
            return Yaml.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(node);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            return new byte[0];
        }
    }

    private Map<String, Path> getPaths(Paths paths) {
        Map<String, Path> swaggerPaths = new java.util.HashMap<>();
        for (Map.Entry<String, PathItem> pathItem : paths.entrySet()) {
            swaggerPaths.put(pathItem.getKey(), convertPathItem(pathItem.getValue()));
        }
        return swaggerPaths;
    }

    private Path convertPathItem(PathItem pathItem) {
        Path path = new Path();
        for (Map.Entry<PathItem.HttpMethod, io.swagger.v3.oas.models.Operation> op : pathItem.readOperationsMap().entrySet()) {
            path.set(op.getKey().name().toLowerCase(), convertOperation(op.getValue()));
        }
        return path;
    }

    private Operation convertOperation(io.swagger.v3.oas.models.Operation openApiOp) {
        Operation swaggerOp = new Operation().operationId(openApiOp.getOperationId());
        if (openApiOp.getTags() != null) {
            swaggerOp.setTags(openApiOp.getTags());
        }
        if (openApiOp.getExtensions() != null) {
            swaggerOp.setVendorExtensions(openApiOp.getExtensions());
        }
        swaggerOp.setSummary(openApiOp.getSummary());
        swaggerOp.setDeprecated(openApiOp.getDeprecated());
        if (openApiOp.getSecurity() != null) {
            for (SecurityRequirement srs : openApiOp.getSecurity()) {
                for (Map.Entry<String, List<String>> sr : srs.entrySet()) {
                    swaggerOp.addSecurity(sr.getKey(), sr.getValue());
                }
            }
        }
        if (openApiOp.getParameters() != null) {
            for (io.swagger.v3.oas.models.parameters.Parameter param : openApiOp.getParameters()) {
                swaggerOp.addParameter(convertParameter(param));
            }
        }
        if (openApiOp.getRequestBody() != null) {
            swaggerOp.addParameter(convertRequestBodyToParameter(openApiOp.getRequestBody()));
            swaggerOp.setConsumes(getConsumers(openApiOp.getRequestBody()));
        }
        if (openApiOp.getResponses() != null) {
            for (Entry<String, ApiResponse> resp : openApiOp.getResponses().entrySet()) {
                swaggerOp.addResponse(resp.getKey(), convertResponse(resp.getValue()));
            }
            swaggerOp.setProduces(getProducers(openApiOp.getResponses().values()));
        }

        return swaggerOp;
    }

    private List<String> getProducers(Collection<ApiResponse> apiResponses) {
        List<String> producers = new java.util.ArrayList<>();
        for (ApiResponse response : apiResponses) {
            if (response.getContent() != null) {
                producers.addAll(response.getContent().keySet());
            }
        }
        return producers;
    }

    private Response convertResponse(ApiResponse apiResponse) {
        Response response = new Response().description(apiResponse.getDescription());
        Map<String, Object> examples = new java.util.HashMap<>();
        if (apiResponse.getContent() != null) {
            for (MediaType contentType : apiResponse.getContent().values()) {
                if (contentType.getSchema() != null) {
                    response.setResponseSchema(convertSchema(contentType.getSchema()));
                    if (contentType.getExamples() != null) {
                        for (Map.Entry<String, Example> ex : contentType.getExamples().entrySet()) {
                            examples.put(ex.getKey(), ex.getValue().getValue());
                        }
                    }
                    break;
                }
            }
        }
        if (!examples.isEmpty()) {
            response.setExamples(examples);
        }
        if (apiResponse.getHeaders() != null) {
            for (Map.Entry<String, Header> hdr : apiResponse.getHeaders().entrySet()) {
                Property headerProp = convertHeaderToProperty(hdr.getValue());
                if (headerProp != null) {
                    response.addHeader(hdr.getKey(), headerProp);
                }
            }
        }
        return response;
    }

    private Property convertHeaderToProperty(Header header) {
        if (header.getSchema() != null) {
            Property headerProp = convertSchemaToProperty(header.getSchema());
            if (header.getDescription() != null) {
                headerProp.setDescription(header.getDescription());
            }
            return headerProp;
        } else {
            log.warn("Missing schema for Header {}", header);
            return null;
        }
    }

    private List<String> getConsumers(RequestBody requestBody) {
        // set consumes for the op to all keys of contentTypes
        if (requestBody.getContent() != null) {
            return new java.util.ArrayList<>(requestBody.getContent().keySet());
        } else {
            return Collections.emptyList();
        }
    }

    private Parameter convertRequestBodyToParameter(RequestBody requestBody) {
        BodyParameter bodyParam = new BodyParameter().name("body");
        if (requestBody.getRequired()) {
            bodyParam.setRequired(requestBody.getRequired());
        }
        if (requestBody.getDescription() != null && !requestBody.getDescription().isEmpty()) {
            bodyParam.setDescription(requestBody.getDescription());
        }
        if (requestBody.getContent() != null) {
            for (MediaType contentType : requestBody.getContent().values()) {
                if (contentType.getSchema() != null) {
                    bodyParam.setSchema(convertSchema(contentType.getSchema()));
                    break;
                }
            }
            for (Entry<String, MediaType> contentType : requestBody.getContent().entrySet()) {
                if (contentType.getValue().getExample() != null) {
                    bodyParam.addExample(contentType.getKey(), contentType.getValue().getExample().toString());
                }
            }

        }
        return bodyParam;
    }

    private Model convertSchema(Schema schema) {
        Model model = null;
        if (schema instanceof FileSchema) {
            // Special case
            return new ModelImpl().type("file");
        }
        if (schema.get$ref() != null) {
            return new RefModel(convertRef(schema.get$ref()));
        } else if (schema.getItems() != null) {
            model = new ArrayModel().items(convertSchemaToProperty(schema.getItems()));
        } else {
            model = new ModelImpl().type(schema.getType()).format(schema.getFormat());
            //           model = new ModelImpl().type(schema.getType());
        }
        if (schema.getProperties() != null) {
            model.setProperties(convertPropertiesMap(schema.getProperties()));
        }
        if (schema.getRequired() != null && model instanceof ModelImpl) {
            for (Object req : schema.getRequired()) {
                ((ModelImpl) model).addRequired(req.toString());
            }
        }
        if (schema.getDescription() != null && !schema.getDescription().isEmpty()) {
            model.setDescription(schema.getDescription());
        }
        if (schema.getExtensions() != null) {
            ((ModelImpl) model).setVendorExtensions(schema.getExtensions());
        }
        return model;
    }

    private Map<String, Property> convertPropertiesMap(Map properties) {
        Map<String, Property> swaggerProps = new java.util.HashMap<>(properties.size());
        for (Map.Entry<String, Schema> propEntry : ((Map<String, Schema>) properties).entrySet()) {
            swaggerProps.put(propEntry.getKey(), convertSchemaToProperty(propEntry.getValue()));
        }
        return swaggerProps;
    }

    private String convertRef(String schemaRef) {
        return schemaRef.replace("#/components/schemas", "#/definitions");
    }

    private Parameter convertParameter(io.swagger.v3.oas.models.parameters.Parameter param) {
        switch (param.getIn()) {
            case "path":
                return initParam(new PathParameter(), param);
            case "query":
                return initParam(new QueryParameter(), param);
            case "cookie":
                return initParam(new CookieParameter(), param);
            case "header":
                return initParam(new HeaderParameter(), param);
            default: // should not happen
                return null;
        }
    }

    private Parameter initParam(
            AbstractSerializableParameter swaggerParam, io.swagger.v3.oas.models.parameters.Parameter param) {
        swaggerParam.name(param.getName()).required(param.getRequired());
        if (param.getDescription() != null) {
            swaggerParam.setDescription(param.getDescription());
        }
        if (param.getStyle() != null) {
            swaggerParam.setCollectionFormat(convertStyleToCollectionFormat(param.getStyle()));
        }
        if (param.getSchema() != null) {
            swaggerParam.setType(param.getSchema().getType());
            swaggerParam.setFormat(param.getSchema().getFormat());
            if (param.getSchema().getDefault() != null) {
                swaggerParam.setDefault(param.getSchema().getDefault());
            }
            if (param.getSchema().getItems() != null) {
                // Convert items schema to swagger items property
                swaggerParam.setItems(convertSchemaToProperty(param.getSchema().getItems()));
            }
            if (param.getSchema().getEnum() != null) {
                // Convert enums (ATTENTION, maybe not strings?)
                List<String> enums
                        = ((List<?>) param.getSchema().getEnum()).stream().map(v -> {
                            if (v instanceof byte[]) {
                                return new String(Base64.getEncoder().encode((byte[]) v));
                            } else if (v instanceof Date) {
                                return RestOpenApiSupport.DEFAULT_DATE_FORMAT.format(v);
                            } else {
                                return v.toString();
                            }
                        }).collect(Collectors.toList());
                swaggerParam.setEnum(enums);
            }
        }
        if (param.getExample() != null) {
            swaggerParam.setExample(param.getExample().toString());
        } else if (param.getExamples() != null && !param.getExamples().isEmpty()) {
            swaggerParam.setExample(param.getExamples().values().iterator().next().getValue().toString());
        }

        return swaggerParam;
    }

    private String convertStyleToCollectionFormat(StyleEnum style) {
        switch (style) {
            case FORM:
                return "csv";
            case SPACEDELIMITED:
                return "ssv";
            case PIPEDELIMITED:
                return "pipes";
            case DEEPOBJECT:
                return "multi";
            default:
                return "csv";
        }
    }

    private Property convertSchemaToProperty(Schema schema) {
        if (schema.get$ref() != null) {
            return new RefProperty(convertRef(schema.get$ref()));
        }
        if (schema instanceof IntegerSchema) {
            IntegerProperty prop = new IntegerProperty();
            prop.setFormat(schema.getFormat());
            return prop;
        } else if (schema instanceof NumberSchema) {
            if ("float".equals(schema.getFormat())) {
                return new io.swagger.models.properties.FloatProperty();
            } else if ("double".equals(schema.getFormat())) {
                return new io.swagger.models.properties.DoubleProperty();
            } else if ("int64".equals(schema.getFormat())) {
                return new io.swagger.models.properties.LongProperty();
            } else {
                return new io.swagger.models.properties.BaseIntegerProperty(schema.getFormat());
            }
        } else if (schema instanceof ByteArraySchema) {
            return new io.swagger.models.properties.ByteArrayProperty();
        } else if (schema instanceof BinarySchema) {
            return new io.swagger.models.properties.BinaryProperty();
        } else if (schema instanceof DateSchema) {
            return new io.swagger.models.properties.DateProperty();
        } else if (schema instanceof DateTimeSchema) {
            return new io.swagger.models.properties.DateTimeProperty();
        } else if (schema instanceof PasswordSchema) {
            return new io.swagger.models.properties.PasswordProperty();
        } else if (schema instanceof FileSchema) {
            return new io.swagger.models.properties.FileProperty();
        } else if (schema instanceof StringSchema) {
            StringProperty prop = new StringProperty(schema.getFormat());
            if (schema.getEnum() != null) {
                prop.setEnum(schema.getEnum());
            }
            return prop;
        } else if (schema instanceof ArraySchema) {
            return new io.swagger.models.properties.ArrayProperty(convertSchemaToProperty(schema.getItems()));
        } else if (schema instanceof MapSchema) {
            if (schema.getAdditionalProperties() != null && schema.getAdditionalProperties() instanceof Schema) {
                return new io.swagger.models.properties.MapProperty(
                        convertSchemaToProperty((Schema) schema.getAdditionalProperties()));
            }
            if (schema.getAdditionalItems() != null) {
                return new io.swagger.models.properties.MapProperty(convertSchemaToProperty(schema.getAdditionalItems()));
            } else {
                return new io.swagger.models.properties.MapProperty(); // should not happen?
            }
        } else {
            // ???
            return PropertyBuilder.build(schema.getType(), schema.getFormat(), null);
        }
    }

    private List<Tag> getTags(List<io.swagger.v3.oas.models.tags.Tag> tags) {
        if (tags != null) {
            List<Tag> swaggerTags = new java.util.ArrayList<>(tags.size());
            for (io.swagger.v3.oas.models.tags.Tag tag : tags) {
                swaggerTags.add(new Tag().name(tag.getName()).description(tag.getDescription()));
            }
            return swaggerTags;
        }
        return null;
    }

    private Info convertInfo(io.swagger.v3.oas.models.info.Info info) {
        if (info != null) {
            return new Info().description(info.getDescription())
                    .title(info.getTitle())
                    .termsOfService(info.getTermsOfService())
                    .version(info.getVersion())
                    .contact(convertContact(info.getContact()))
                    .license(convertLicense(info.getLicense()));
        } else {
            return null;
        }
    }

    private License convertLicense(io.swagger.v3.oas.models.info.License license) {
        if (license != null) {
            return new License().name(license.getName()).url(license.getUrl());
        } else {
            return null;
        }
    }

    private Contact convertContact(io.swagger.v3.oas.models.info.Contact contact) {
        if (contact != null) {
            return new Contact().name(contact.getName()).email(contact.getEmail()).url(contact.getUrl());
        } else {
            return null;
        }
    }

}
