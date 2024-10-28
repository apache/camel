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
package org.apache.camel.component.smooks.converter;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.w3c.dom.Node;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.TypeConverterRegistry;
import org.smooks.io.sink.DOMSink;
import org.smooks.io.sink.JavaSink;
import org.smooks.io.sink.StringSink;
import org.smooks.io.source.StringSource;

/**
 * Converts from different {@link org.smooks.api.io.Sink} types.
 */
@Converter(generateLoader = true)
public class SinkConverter {

    public static final String SMOOKS_RESULT_KEY = "SmooksResultKeys";

    private SinkConverter() {
    }

    @Converter
    public static Node toDocument(DOMSink domSink) {
        return domSink.getNode();
    }

    @SuppressWarnings("rawtypes")
    @Converter
    public static List toList(JavaSink.ResultMap javaResult, Exchange exchange) {
        String resultKey = (String) exchange.getProperty(SMOOKS_RESULT_KEY);
        if (resultKey != null) {
            return (List) getResultsFromJavaSink(javaResult, resultKey);
        } else {
            return (List) getSingleObjectFromJavaSink(javaResult);
        }
    }

    @SuppressWarnings("rawtypes")
    @Converter
    public static Integer toInteger(JavaSink.ResultMap result) {
        return (Integer) getSingleObjectFromJavaSink(result);
    }

    @SuppressWarnings("rawtypes")
    @Converter
    public static Double toDouble(JavaSink.ResultMap result) {
        return (Double) getSingleObjectFromJavaSink(result);
    }

    @Converter
    public static String toString(StringSink result) {
        return result.getResult();
    }

    @SuppressWarnings("rawtypes")
    public static Map toMap(JavaSink.ResultMap resultBeans, Exchange exchange) {
        Message outMessage = exchange.getOut();
        outMessage.setBody(resultBeans);

        @SuppressWarnings("unchecked")
        Set<Entry<String, Object>> entrySet = resultBeans.entrySet();
        for (Entry<String, Object> entry : entrySet) {
            outMessage.setBody(entry.getValue(), entry.getValue().getClass());
        }
        return resultBeans;
    }

    @SuppressWarnings("rawtypes")
    private static Object getResultsFromJavaSink(JavaSink.ResultMap resultMap, String resultKey) {
        return resultMap.get(resultKey);
    }

    private static Object getSingleObjectFromJavaSink(@SuppressWarnings("rawtypes") JavaSink.ResultMap resultMap) {
        if (resultMap.size() == 1) {
            return resultMap.values().iterator().next();
        }
        return null;
    }

    @Converter
    public static StringSource toStringSource(StringSink stringResult) {
        String result = stringResult.getResult();
        return new StringSource(result);
    }

    @SuppressWarnings("rawtypes")
    @Converter(fallback = true)
    public static <T> T convertTo(Class<T> type, Exchange exchange, Object value, TypeConverterRegistry registry) {
        if (value instanceof JavaSink.ResultMap) {
            for (Object mapValue : ((Map) value).values()) {
                if (type.isInstance(mapValue)) {
                    return type.cast(mapValue);
                }
            }
        }

        return null;
    }
}
