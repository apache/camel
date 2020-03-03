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
package org.apache.camel.dataformat.protobuf;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import org.apache.camel.util.ObjectHelper;

public final class ProtobufConverter {

    private ProtobufConverter() {
    }

    public static Message toProto(final Map<?, ?> inputData, final Message defaultInstance) {
        ObjectHelper.notNull(inputData, "inputData");
        ObjectHelper.notNull(defaultInstance, "defaultInstance");

        final Descriptor descriptor = defaultInstance.getDescriptorForType();
        final Builder target = defaultInstance.newBuilderForType();

        return convertMapToMessage(descriptor, target, inputData);
    }

    private static Message convertMapToMessage(final Descriptor descriptor, final Builder builder, final Map<?, ?> inputData) {
        ObjectHelper.notNull(descriptor, "descriptor");
        ObjectHelper.notNull(builder, "builder");
        ObjectHelper.notNull(inputData, "inputData");

        // we set our values from map to descriptor
        inputData.forEach((key, value) -> {
            final FieldDescriptor fieldDescriptor = descriptor.findFieldByName(key.toString());
            // if we don't find our desired fieldDescriptor, we just ignore it
            if (fieldDescriptor != null) {
                if (fieldDescriptor.isRepeated()) {
                    final List<?> repeatedValues = castValue(value, List.class, String.format("Not able to cast value to list, make sure you have a list for the repeated field '%s'", fieldDescriptor.getName()));
                    repeatedValues.forEach(repeatedValue -> builder.addRepeatedField(fieldDescriptor, getSuitableFieldValue(fieldDescriptor, builder, repeatedValue)));
                } else {
                    builder.setField(fieldDescriptor, getSuitableFieldValue(fieldDescriptor, builder, value));
                }
            }
        });
        return builder.build();
    }

    private static Object getSuitableFieldValue(final FieldDescriptor fieldDescriptor, final Builder builder, final Object inputValue) {
        ObjectHelper.notNull(fieldDescriptor, "fieldDescriptor");
        ObjectHelper.notNull(builder, "builder");
        ObjectHelper.notNull(inputValue, "inputValue");

        switch (fieldDescriptor.getJavaType()) {
            case ENUM:
                return getEnumValue(fieldDescriptor, inputValue);

            case MESSAGE:
                final Map<?, ?> nestedValue = castValue(inputValue, Map.class, String.format("Not able to cast value to map, make sure you have a map for the nested field message '%s'", fieldDescriptor.getName()));
                // we do a nested call until we reach our final message
                return convertMapToMessage(fieldDescriptor.getMessageType(), builder.newBuilderForField(fieldDescriptor), nestedValue);

            default:
                return inputValue;
        }
    }

    private static EnumValueDescriptor getEnumValue(final FieldDescriptor fieldDescriptor, final Object value) {
        final EnumValueDescriptor enumValueDescriptor = getSuitableEnumValue(fieldDescriptor, value);

        if (enumValueDescriptor == null) {
            throw new IllegalArgumentException(String.format("Could not retrieve enum index '%s' for enum field '%s', most likely the index does not exist in the enum indexes '%s'",
                    value, fieldDescriptor.getName(), fieldDescriptor.getEnumType().getValues()));
        }

        return enumValueDescriptor;
    }

    private static EnumValueDescriptor getSuitableEnumValue(final FieldDescriptor fieldDescriptor, final Object value) {
        // we check if value is string, we find index by name, otherwise by integer
        if (value instanceof String) {
            return fieldDescriptor.getEnumType().findValueByName((String) value);
        } else {
            final int index = castValue(value, Integer.class, String.format("Not able to cast value to integer, make sure you have an integer index for the enum field '%s'", fieldDescriptor.getName()));
            return fieldDescriptor.getEnumType().findValueByNumber(index);
        }
    }

    public static Map<String, Object> toMap(final Message inputProto) {
        return convertProtoMessageToMap(inputProto);
    }

    private static Map<String, Object> convertProtoMessageToMap(final Message inputData) {
        ObjectHelper.notNull(inputData, "inputData");

        final Map<Descriptors.FieldDescriptor, Object> allFields = inputData.getAllFields();

        final Map<String, Object> mapResult = new LinkedHashMap<>();

        // we set our values from descriptors to map
        allFields.forEach((fieldDescriptor, value) -> {
            final String fieldName = fieldDescriptor.getName();
            if (fieldDescriptor.isRepeated()) {
                final List<?> repeatedValues = castValue(value, List.class, String.format("Not able to cast value to list, make sure you have a list for the repeated field '%s'", fieldName));
                mapResult.put(fieldName, repeatedValues.stream().map(singleValue -> convertValueToSuitableFieldType(singleValue, fieldDescriptor)).collect(Collectors.toList()));
            } else {
                mapResult.put(fieldName, convertValueToSuitableFieldType(value, fieldDescriptor));
            }
        });

        return mapResult;
    }

    private static Object convertValueToSuitableFieldType(final Object value, final Descriptors.FieldDescriptor fieldDescriptor) {
        ObjectHelper.notNull(fieldDescriptor, "fieldDescriptor");
        ObjectHelper.notNull(value, "value");

        Object result;

        switch (fieldDescriptor.getJavaType()) {
            case ENUM:
            case BYTE_STRING:
                result = value.toString();
                break;
            case MESSAGE:
                result = convertProtoMessageToMap((Message)value);
                break;
            default:
                result = value;
                break;
        }

        return result;
    }

    private static <T> T castValue(final Object value, final Class<T> type, final String errorMessage) {
        try {
            return type.cast(value);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(errorMessage, e);
        }
    }
}
