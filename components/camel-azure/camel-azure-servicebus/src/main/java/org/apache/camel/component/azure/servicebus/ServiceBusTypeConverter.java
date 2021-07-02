package org.apache.camel.component.azure.servicebus;

import com.azure.core.util.BinaryData;
import org.apache.camel.Converter;
import org.apache.camel.util.ObjectHelper;

@Converter(generateLoader = true)
public final class ServiceBusTypeConverter {

    private ServiceBusTypeConverter() {
    }

    @Converter
    public static String toString(final BinaryData binaryData) {
        if (ObjectHelper.isNotEmpty(binaryData)) {
            return binaryData.toString();
        }
        return null;
    }

    @Converter
    public static BinaryData toBinaryData(final String data) {
        if (ObjectHelper.isNotEmpty(data)) {
            return BinaryData.fromString(data);
        }
        return null;
    }
}
