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
package org.apache.camel.component.aws2.s3vectors;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.Jsoner;
import software.amazon.awssdk.core.document.Document;

/**
 * Converts the values that the routes give to the component (keys, metadata, metadata filters) into the types of the S3
 * Vectors requests.
 */
final class AWS2S3VectorsHelper {

    private AWS2S3VectorsHelper() {
    }

    /**
     * Parses a metadata filter given as JSON text into the filter document of a query.
     *
     * @param  json   the JSON object of the filter
     * @param  source the header or the option that holds the filter, for the error message
     * @return        the filter document
     */
    static Document parseMetadataFilter(String json, String source) {
        Object parsed;
        try {
            parsed = Jsoner.deserialize(json);
        } catch (DeserializationException e) {
            throw new IllegalArgumentException(
                    "The " + source + " must be a JSON object such as {\"genre\": {\"$eq\": \"drama\"}}, but it is not"
                                               + " valid JSON: " + e.getMessage(),
                    e);
        }
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException(
                    "The " + source + " must be a JSON object such as {\"genre\": {\"$eq\": \"drama\"}}, but it is a "
                                               + (parsed == null ? "null" : "JSON " + parsed.getClass().getSimpleName()));
        }
        return jsonToDocument(parsed);
    }

    private static Document jsonToDocument(Object value) {
        if (value == null) {
            return Document.fromNull();
        } else if (value instanceof String str) {
            return Document.fromString(str);
        } else if (value instanceof Boolean bool) {
            return Document.fromBoolean(bool);
        } else if (value instanceof Number number) {
            return numberToDocument(number);
        } else if (value instanceof Map<?, ?> map) {
            Map<String, Document> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), jsonToDocument(v)));
            return Document.fromMap(result);
        } else if (value instanceof Collection<?> list) {
            return Document.fromList(list.stream().map(AWS2S3VectorsHelper::jsonToDocument).toList());
        }
        // the JSON parser gives only the types above
        throw new IllegalArgumentException("Unexpected JSON value of type " + value.getClass().getName());
    }

    /**
     * Converts the metadata of a vector into a document. The values may be Strings, numbers, booleans and lists of
     * these.
     */
    static Document metadataToDocument(Map<?, ?> metadata) {
        Map<String, Document> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : metadata.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            Document document = scalarToDocument(value);
            if (document == null) {
                List<?> list = asList(value);
                if (list == null) {
                    throw new IllegalArgumentException(
                            "The metadata value of key " + key + " is " + describe(value)
                                                       + ": use a String, a number, a boolean or a list of these");
                }
                List<Document> elements = new ArrayList<>(list.size());
                for (Object element : list) {
                    Document elementDocument = scalarToDocument(element);
                    if (elementDocument == null) {
                        throw new IllegalArgumentException(
                                "The metadata value of key " + key + " is a list with an element that is "
                                                           + describe(element)
                                                           + ": the elements must be Strings, numbers or booleans");
                    }
                    elements.add(elementDocument);
                }
                document = Document.fromList(elements);
            }
            result.put(key, document);
        }
        return Document.fromMap(result);
    }

    private static Document scalarToDocument(Object value) {
        if (value instanceof CharSequence str) {
            return Document.fromString(str.toString());
        } else if (value instanceof Boolean bool) {
            return Document.fromBoolean(bool);
        } else if (value instanceof Number number) {
            return numberToDocument(number);
        }
        return null;
    }

    private static Document numberToDocument(Number number) {
        if (number instanceof Integer || number instanceof Short || number instanceof Byte) {
            return Document.fromNumber(number.intValue());
        } else if (number instanceof Long l) {
            return Document.fromNumber(l);
        } else if (number instanceof Float f) {
            return Document.fromNumber(f);
        } else if (number instanceof Double d) {
            return Document.fromNumber(d);
        } else if (number instanceof BigDecimal bd) {
            return Document.fromNumber(bd);
        } else if (number instanceof BigInteger bi) {
            return Document.fromNumber(bi);
        }
        return Document.fromNumber(number.toString());
    }

    /**
     * The vector keys that the value holds: a String, or a non-empty Collection or array whose elements are all
     * Strings.
     *
     * @return the keys, or null when the value holds no keys
     */
    static List<String> asKeys(Object value) {
        if (value instanceof CharSequence str) {
            return str.isEmpty() ? null : List.of(str.toString());
        }
        List<?> list = asList(value);
        if (list == null || list.isEmpty()) {
            return null;
        }
        List<String> keys = new ArrayList<>(list.size());
        for (Object element : list) {
            if (!(element instanceof CharSequence str)) {
                return null;
            }
            keys.add(str.toString());
        }
        return keys;
    }

    /**
     * Describes a value that holds no keys or no valid metadata, for an error message.
     */
    static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        List<?> list = asList(value);
        if (list != null) {
            if (list.isEmpty()) {
                return "an empty " + value.getClass().getName();
            }
            for (Object element : list) {
                if (!(element instanceof CharSequence)) {
                    return "a " + value.getClass().getName() + " with an element of type "
                           + (element == null ? "null" : element.getClass().getName());
                }
            }
        }
        return "of type " + value.getClass().getName();
    }

    private static List<?> asList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        } else if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        } else if (value instanceof Object[] array) {
            return Arrays.asList(array);
        }
        return null;
    }
}
