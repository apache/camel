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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import groovy.json.DefaultJsonGenerator;
import groovy.json.JsonGenerator;
import org.apache.groovy.json.internal.CharBuf;

/**
 * Groovy's {@link DefaultJsonGenerator} writing the layout of {@link groovy.json.JsonOutput#prettyPrint(String)} in one
 * pass: only the container methods are overridden to add newlines and a four-space indentation, every scalar, date,
 * enum and POJO is still formatted by Groovy. {@code JsonOutput.prettyPrint} re-lexes the generated text with a
 * regular-expression tokenizer, which cost 30x a Jackson pretty print.
 * <p>
 * Not thread safe: one instance per document.
 */
final class PrettyJsonGenerator extends DefaultJsonGenerator {

    private static final char[] INDENT = "    ".toCharArray();
    private static final char[] SEPARATOR = ": ".toCharArray();

    private int depth;

    PrettyJsonGenerator() {
        super(new JsonGenerator.Options());
    }

    @Override
    protected void writeMap(Map<?, ?> map, CharBuf buffer) {
        if (map.isEmpty()) {
            empty('{', '}', buffer);
            return;
        }
        buffer.addChar('{');
        depth++;
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("Maps with null keys can't be converted to JSON");
            }
            String key = entry.getKey().toString();
            Object value = entry.getValue();
            if (isExcludingValues(value) || isExcludingFieldsNamed(key)) {
                continue;
            }
            if (first) {
                first = false;
            } else {
                buffer.addChar(',');
            }
            newLine(buffer);
            buffer.addJsonEscapedString(key, disableUnicodeEscaping).addChars(SEPARATOR);
            writeObject(key, value, buffer);
        }
        depth--;
        newLine(buffer);
        buffer.addChar('}');
    }

    @Override
    protected void writeIterator(Iterator<?> iterator, CharBuf buffer) {
        if (!iterator.hasNext()) {
            empty('[', ']', buffer);
            return;
        }
        buffer.addChar('[');
        depth++;
        boolean first = true;
        while (iterator.hasNext()) {
            Object item = iterator.next();
            if (isExcludingValues(item)) {
                continue;
            }
            if (first) {
                first = false;
            } else {
                buffer.addChar(',');
            }
            newLine(buffer);
            writeObject(item, buffer);
        }
        depth--;
        newLine(buffer);
        buffer.addChar(']');
    }

    @Override
    protected void writeArray(Class<?> arrayClass, Object array, CharBuf buffer) {
        // groovy writes primitive arrays compactly; the pretty layout lists every element on its own line
        int length = Array.getLength(array);
        List<Object> items = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            Object item = Array.get(array, i);
            items.add(item instanceof Character c ? String.valueOf(c) : item);
        }
        writeIterator(items.iterator(), buffer);
    }

    /**
     * An empty container is an opening bracket, an indented blank line and the closing bracket, as
     * {@code JsonOutput.prettyPrint} writes it.
     */
    private void empty(char open, char close, CharBuf buffer) {
        buffer.addChar(open);
        depth++;
        newLine(buffer);
        depth--;
        newLine(buffer);
        buffer.addChar(close);
    }

    private void newLine(CharBuf buffer) {
        buffer.addChar('\n');
        for (int i = 0; i < depth; i++) {
            buffer.addChars(INDENT);
        }
    }
}
