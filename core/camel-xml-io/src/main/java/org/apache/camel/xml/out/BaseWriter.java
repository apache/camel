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
package org.apache.camel.xml.out;

import java.io.IOException;
import java.io.Writer;

import org.apache.camel.xml.io.XMLWriter;

public class BaseWriter {

    protected final XMLWriter writer;
    protected final String namespace;
    protected boolean namespaceWritten;

    public BaseWriter(Writer writer, String namespace) throws IOException {
        this.writer = new XMLWriter(writer);
        this.namespace = namespace;
    }

    protected void startElement(String name) throws IOException {
        writer.startElement(name);
        if (!namespaceWritten && namespace != null) {
            writer.addAttribute("xmlns", namespace);
            namespaceWritten = true;
        }
    }

    protected void endElement() throws IOException {
        writer.endElement();
    }

    protected void text(String text) throws IOException {
        writer.writeText(text);
    }

    protected void attribute(String name, String value) throws IOException {
        writer.addAttribute(name, value);
    }
}
