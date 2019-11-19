package org.apache.camel.dataformat.protobuf;

import java.util.Map;

import com.google.protobuf.Message;
import org.apache.camel.Converter;

//@Converter(generateLoader = true)
public class ProtobufTypeConverter {

    //@Converter
    public static Map<String, Object> toMap(final Message message) {
        return ProtobufConverter.toMap(message);
    }
}
