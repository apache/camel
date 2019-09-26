package org.apache.camel.dataformat.protobuf;

import java.util.List;
import java.util.Map;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import org.apache.camel.CamelException;
import org.apache.camel.util.ObjectHelper;

public class ProtobufConverter {

    private final Message defaultInstance;

    private ProtobufConverter(final Message defaultInstance) {
        this.defaultInstance = defaultInstance;
    }

    public static ProtobufConverter create(final Message defaultInstance) {
        ObjectHelper.notNull(defaultInstance, "defaultInstance");

        return new ProtobufConverter(defaultInstance);
    }

    public Message toProto(final Map<?, ?> inputData) {
        ObjectHelper.notNull(inputData, "inputData");

        final Descriptors.Descriptor descriptor = defaultInstance.getDescriptorForType();
        final Message.Builder target = defaultInstance.newBuilderForType();

        return convertMapToMessage(descriptor, target, inputData);
    }

    private static Message convertMapToMessage(final Descriptors.Descriptor descriptor, final Message.Builder builder, final Map<?, ?> inputData) {
        ObjectHelper.notNull(descriptor, "descriptor");
        ObjectHelper.notNull(builder, "builder");

        inputData.forEach((key, value) -> {
            final Descriptors.FieldDescriptor fieldDescriptor = descriptor.findFieldByName(key.toString());
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

    private static Object getSuitableFieldValue(final Descriptors.FieldDescriptor fieldDescriptor, final Message.Builder builder, final Object inputValue) {
        ObjectHelper.notNull(fieldDescriptor, "fieldDescriptor");
        ObjectHelper.notNull(builder, "builder");

        switch (fieldDescriptor.getJavaType()) {
            case ENUM:
                final int index = castValue(inputValue, Integer.class, String.format("Not able to cast value to integer, make sure you have an integer index for the enum field '%s'", fieldDescriptor.getName()));
                return getEnumValue(fieldDescriptor, index);

            case MESSAGE:
                final Map<?, ?> nestedValue = castValue(inputValue, Map.class, String.format("Not able to cast value to map, make sure you have a map for the nested field message '%s'", fieldDescriptor.getName()));
                return convertMapToMessage(fieldDescriptor.getMessageType(), builder.newBuilderForField(fieldDescriptor), nestedValue);

            default:
                return inputValue;
        }
    }

    private static Descriptors.EnumValueDescriptor getEnumValue(final Descriptors.FieldDescriptor fieldDescriptor, final int index) {
        final Descriptors.EnumValueDescriptor enumValueDescriptor = fieldDescriptor.getEnumType().findValueByNumber(index);

        if (enumValueDescriptor == null) {
            throw new IllegalArgumentException(String.format("Could not retrieve enum index '%s' for enum field '%s', most likely the index does not exist in the enum indexes '%s'",
                    index, fieldDescriptor.getName(), fieldDescriptor.getEnumType().getValues()));
        }

        return enumValueDescriptor;
    }

    private static <T> T castValue(final Object value, final Class<T> type, final String errorMessage) {
        try {
            return type.cast(value);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(errorMessage, e);
        }
    }
}
