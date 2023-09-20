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
package org.apache.camel.component.salesforce.api.utils;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonValueFormat;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerFactory;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.SerializerFactory;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.databind.ser.std.NullSerializer;
import com.fasterxml.jackson.module.jsonSchema.jakarta.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.jakarta.JsonSchemaGenerator;
import com.fasterxml.jackson.module.jsonSchema.jakarta.types.ArraySchema;
import com.fasterxml.jackson.module.jsonSchema.jakarta.types.BooleanSchema;
import com.fasterxml.jackson.module.jsonSchema.jakarta.types.IntegerSchema;
import com.fasterxml.jackson.module.jsonSchema.jakarta.types.NullSchema;
import com.fasterxml.jackson.module.jsonSchema.jakarta.types.NumberSchema;
import com.fasterxml.jackson.module.jsonSchema.jakarta.types.ObjectSchema;
import com.fasterxml.jackson.module.jsonSchema.jakarta.types.SimpleTypeSchema;
import com.fasterxml.jackson.module.jsonSchema.jakarta.types.StringSchema;
import org.apache.camel.component.salesforce.api.FieldsToNullPropertyFilter;
import org.apache.camel.component.salesforce.api.dto.AbstractDTOBase;
import org.apache.camel.component.salesforce.api.dto.AbstractQueryRecordsBase;
import org.apache.camel.component.salesforce.api.dto.Address;
import org.apache.camel.component.salesforce.api.dto.GeoLocation;
import org.apache.camel.component.salesforce.api.dto.GlobalObjects;
import org.apache.camel.component.salesforce.api.dto.PickListValue;
import org.apache.camel.component.salesforce.api.dto.SObject;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.SObjectField;
import org.apache.camel.impl.engine.DefaultPackageScanClassResolver;

import static java.util.stream.Collectors.joining;

/**
 * Factory class for creating {@linkplain com.fasterxml.jackson.databind.ObjectMapper}
 */
public final class JsonUtils {

    public static final String SCHEMA4 = "http://json-schema.org/draft-04/schema#";
    public static final String DEFAULT_ID_PREFIX = "urn:jsonschema:org:apache:camel:component:salesforce:dto";

    private static final String API_DTO_ID = "org:urn:jsonschema:org:apache:camel:component:salesforce:api:dto";

    private JsonUtils() {

    }

    public static ObjectMapper createObjectMapper() {
        // enable date time support including Java 1.8 ZonedDateTime
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleFilterProvider filterProvider
                = new SimpleFilterProvider().addFilter("fieldsToNull", new FieldsToNullPropertyFilter());
        objectMapper.setFilterProvider(filterProvider);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false);
        objectMapper.registerModule(new TimeModule());

        return objectMapper;
    }

    public static String getBasicApiJsonSchema() throws JsonProcessingException {
        ObjectMapper mapper = createSchemaObjectMapper();
        JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(mapper);

        DefaultPackageScanClassResolver packageScanClassResolver = new DefaultPackageScanClassResolver();

        Set<Class<?>> schemaClasses = new HashSet<>();

        // get non-abstract extensions of AbstractDTOBase
        schemaClasses.addAll(packageScanClassResolver.findByFilter(
                type -> !Modifier.isAbstract(type.getModifiers()) && AbstractDTOBase.class.isAssignableFrom(type),
                "org.apache.camel.component.salesforce.api.dto"));

        // get non-abstract extensions of AbstractDTOBase
        schemaClasses.addAll(packageScanClassResolver.findByFilter(
                type -> !Modifier.isAbstract(type.getModifiers()) && AbstractDTOBase.class.isAssignableFrom(type),
                "org.apache.camel.component.salesforce.api.dto"));

        Set<JsonSchema> allSchemas = new HashSet<>();
        for (Class<?> aClass : schemaClasses) {
            JsonSchema jsonSchema = schemaGen.generateSchema(aClass);
            allSchemas.add(jsonSchema);
        }

        return getJsonSchemaString(mapper, allSchemas, API_DTO_ID);
    }

    public static String getJsonSchemaString(ObjectMapper mapper, Set<JsonSchema> allSchemas, String id)
            throws JsonProcessingException {
        JsonSchema rootSchema = getJsonSchemaAsSchema(allSchemas, id);

        return mapper.writeValueAsString(rootSchema);
    }

    public static JsonSchema getJsonSchemaAsSchema(Set<JsonSchema> allSchemas, String id) {
        ObjectSchema rootSchema = new ObjectSchema();
        rootSchema.set$schema(SCHEMA4);
        rootSchema.setId(id);
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Set<Object> tmp = (Set) allSchemas;
        rootSchema.setOneOf(tmp);

        return rootSchema;
    }

    public static String getSObjectJsonSchema(SObjectDescription description) throws JsonProcessingException {
        return getSObjectJsonSchema(description, true);
    }

    public static JsonSchema getSObjectJsonSchemaAsJson(SObjectDescription description) throws JsonProcessingException {
        return getSObjectJsonSchemaAsSchema(description, true);
    }

    public static String getSObjectJsonSchema(SObjectDescription description, boolean addQuerySchema)
            throws JsonProcessingException {
        ObjectMapper schemaObjectMapper = createSchemaObjectMapper();
        return getJsonSchemaString(schemaObjectMapper,
                getSObjectJsonSchema(schemaObjectMapper, description, DEFAULT_ID_PREFIX, addQuerySchema), DEFAULT_ID_PREFIX);
    }

    public static JsonSchema getSObjectJsonSchemaAsSchema(SObjectDescription description, boolean addQuerySchema)
            throws JsonProcessingException {
        ObjectMapper schemaObjectMapper = createSchemaObjectMapper();
        return getJsonSchemaAsSchema(getSObjectJsonSchema(schemaObjectMapper, description, DEFAULT_ID_PREFIX, addQuerySchema),
                DEFAULT_ID_PREFIX);
    }

    public static Set<JsonSchema> getSObjectJsonSchema(
            ObjectMapper objectMapper, SObjectDescription description, String idPrefix, boolean addQuerySchema)
            throws JsonProcessingException {
        Set<JsonSchema> allSchemas = new HashSet<>();

        // generate SObject schema from description
        ObjectSchema sobjectSchema = new ObjectSchema();
        sobjectSchema.setId(idPrefix + ":" + description.getName());
        sobjectSchema.setTitle(description.getLabel());

        SimpleTypeSchema addressSchema = null;
        SimpleTypeSchema geoLocationSchema = null;

        for (SObjectField field : description.getFields()) {

            SimpleTypeSchema fieldSchema = new NullSchema();
            String soapType = field.getSoapType();

            switch (soapType.substring(soapType.indexOf(':') + 1)) {
                case "ID": // mapping for tns:ID SOAP type
                case "string":
                case "base64Binary":
                    // Salesforce maps any types like string, picklist, reference,
                    // etc. to string
                case "anyType":
                    fieldSchema = new StringSchema();
                    break;

                case "integer":
                case "int":
                case "long":
                case "short":
                case "byte":
                case "unsignedInt":
                case "unsignedShort":
                case "unsignedByte":
                    fieldSchema = new IntegerSchema();
                    break;

                case "decimal":
                case "float":
                case "double":
                    fieldSchema = new NumberSchema();
                    break;

                case "boolean":
                    fieldSchema = new BooleanSchema();
                    break;

                case "date":
                    fieldSchema = new StringSchema();
                    ((StringSchema) fieldSchema).setFormat(JsonValueFormat.DATE);
                    break;
                case "dateTime":
                case "g":
                    fieldSchema = new StringSchema();
                    ((StringSchema) fieldSchema).setFormat(JsonValueFormat.DATE_TIME);
                    break;
                case "time":
                    fieldSchema = new StringSchema();
                    ((StringSchema) fieldSchema).setFormat(JsonValueFormat.TIME);
                    break;

                case "address":
                    if (addressSchema == null) {
                        addressSchema = getSchemaFromClass(objectMapper, Address.class);
                    }
                    fieldSchema = addressSchema;
                    break;

                case "location":
                    if (geoLocationSchema == null) {
                        geoLocationSchema = getSchemaFromClass(objectMapper, GeoLocation.class);
                    }
                    fieldSchema = geoLocationSchema;
                    break;

                default:
                    throw new IllegalArgumentException("Unsupported type " + soapType);
            }

            List<PickListValue> picklistValues = field.getPicklistValues();
            switch (field.getType()) {
                case "picklist":
                    fieldSchema.asStringSchema()
                            .setEnums(picklistValues == null
                                    ? Collections.emptySet() : picklistValues.stream().map(PickListValue::getValue).distinct()
                                            .collect(Collectors.toSet()));
                    break;

                case "multipicklist":
                    // TODO regex needs more work to not allow values not separated
                    // by ','
                    fieldSchema.asStringSchema()
                            .setPattern(picklistValues == null
                                    ? "" : picklistValues.stream().map(val -> "(,?(" + val.getValue() + "))").distinct()
                                            .collect(joining("|", "(", ")")));
                    break;

                default:
                    // nothing to fix
            }

            // additional field properties
            fieldSchema.setTitle(field.getLabel());
            fieldSchema.setDefault(field.getDefaultValue());
            if (field.isUpdateable() != null) {
                fieldSchema.setReadonly(!field.isUpdateable());
            }

            final String descriptionText = Arrays
                    .asList(new Object[] { "unique", field.isUnique() }, new Object[] { "idLookup", field.isIdLookup() },
                            new Object[] { "autoNumber", field.isAutoNumber() },
                            new Object[] { "calculated", field.isCalculated() })
                    .stream().filter(ary -> Boolean.TRUE.equals(ary[1])).map(ary -> String.valueOf(ary[0]))
                    .collect(Collectors.joining(","));
            // JSON schema currently does not support the above attributes so
            // we'll store this information
            // in the description
            fieldSchema.setDescription(descriptionText);

            // add property to sobject schema
            if (field.isNillable()) {
                sobjectSchema.putOptionalProperty(field.getName(), fieldSchema);
            } else {
                sobjectSchema.putProperty(field.getName(), fieldSchema);
            }
        }

        // add sobject schema to root schema
        allSchemas.add(sobjectSchema);

        if (addQuerySchema) {
            // add a simple query schema for this sobject for lookups, etc.
            ObjectSchema queryRecords = getSchemaFromClass(objectMapper, AbstractQueryRecordsBase.class);
            queryRecords.setId(idPrefix + ":QueryRecords" + description.getName());

            ObjectSchema refSchema = new ObjectSchema();
            refSchema.set$ref(sobjectSchema.getId());

            ArraySchema recordsProperty = new ArraySchema();
            recordsProperty.setItems(new ArraySchema.SingleItems(refSchema));
            queryRecords.putProperty("records", recordsProperty);

            allSchemas.add(queryRecords);
        }

        return allSchemas;
    }

    public static ObjectMapper createSchemaObjectMapper() {
        ObjectMapper objectMapper = createObjectMapper();
        objectMapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);
        objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        return objectMapper;
    }

    private static ObjectSchema getSchemaFromClass(ObjectMapper objectMapper, Class<?> type) throws JsonMappingException {
        return new JsonSchemaGenerator(objectMapper).generateSchema(type).asObjectSchema();
    }

    public static JsonSchema getGlobalObjectsJsonSchemaAsSchema(final GlobalObjects globalObjects) {
        final Set<JsonSchema> allSchemas = new HashSet<>();

        for (SObject sobject : globalObjects.getSobjects()) {
            // generate SObject schema from description
            ObjectSchema sobjectSchema = new ObjectSchema();
            sobjectSchema.setId(DEFAULT_ID_PREFIX + ":" + sobject.getName());
            sobjectSchema.setTitle(sobject.getLabel());

            allSchemas.add(sobjectSchema);
        }

        return getJsonSchemaAsSchema(allSchemas, DEFAULT_ID_PREFIX + ":GlobalObjects");
    }

    public static ObjectMapper withNullSerialization(final ObjectMapper objectMapper) {
        final SerializerFactory factory = BeanSerializerFactory.instance.withSerializerModifier(new BeanSerializerModifier() {
            @Override
            public JsonSerializer<?> modifySerializer(
                    final SerializationConfig config, final BeanDescription beanDesc, final JsonSerializer<?> serializer) {
                for (final PropertyWriter writer : (Iterable<PropertyWriter>) serializer::properties) {
                    if (writer instanceof BeanPropertyWriter) {
                        ((BeanPropertyWriter) writer).assignNullSerializer(NullSerializer.instance);
                    }
                }

                return serializer;
            }
        });

        return objectMapper.copy().setSerializerFactory(factory);
    }

}
