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
package org.apache.camel.yaml.out;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.w3c.dom.Element;

import org.apache.camel.yaml.io.YamlWriter;

public class BaseWriter {

    protected final YamlWriter writer;

    public BaseWriter(Writer writer, String namespace) throws IOException {
        this.writer = new YamlWriter(writer);
        // namespace is only for XML
    }

    public void setUriAsParameters(boolean uriAsParameters) {
        this.writer.setUriAsParameters(uriAsParameters);
    }

    public String toYaml() {
        return writer.toYaml();
    }

    protected void startElement(String name) throws IOException {
        writer.startElement(name);
    }

    protected void startExpressionElement(String name) throws IOException {
        writer.startExpressionElement(name);
    }

    protected void endElement(String name) throws IOException {
        writer.endElement(name);
    }

    protected void endExpressionElement(String name) throws IOException {
        writer.endExpressionElement(name);
    }

    protected void text(String name, String text) throws IOException {
        writer.writeText(name, text);
    }

    protected void value(String value) throws IOException {
        writer.writeValue(value);
    }

    protected void attribute(String name, Object value) throws IOException {
        if (value != null) {
            writer.addAttribute(name, value);
        }
    }

    protected void domElements(List<Element> elements) throws IOException {
        // not in use for yaml-dsl
    }

}
