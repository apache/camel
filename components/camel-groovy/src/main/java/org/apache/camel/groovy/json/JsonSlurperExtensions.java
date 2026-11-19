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
package org.apache.camel.groovy.json;

import java.io.InputStream;

import groovy.json.JsonSlurper;

/**
 * Groovy extension methods that let {@code new JsonSlurper().parseText(body)} read a message body that is not text: the
 * byte[] a JMS consumer gets for a BytesMessage, a Kafka record read with the ByteArrayDeserializer, or the stream of
 * an HTTP request. In Groovy, parseText takes only a String, and JsonSlurper.parse reads bytes and streams but has no
 * form for a String, so no single call took both kinds of body (CAMEL-27150).
 * <p/>
 * Registered as a Groovy extension module in META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule. The methods
 * only add calls that failed before, with "No signature of method: parseText ... ([B)".
 */
public final class JsonSlurperExtensions {

    private JsonSlurperExtensions() {
    }

    /**
     * Parses JSON bytes, as {@link JsonSlurper#parse(byte[])} does.
     */
    public static Object parseText(JsonSlurper self, byte[] bytes) {
        return self.parse(bytes);
    }

    /**
     * Parses JSON from a stream, as {@link JsonSlurper#parse(InputStream)} does.
     */
    public static Object parseText(JsonSlurper self, InputStream stream) {
        return self.parse(stream);
    }
}
