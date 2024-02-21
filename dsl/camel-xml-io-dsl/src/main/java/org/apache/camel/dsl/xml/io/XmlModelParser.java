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
package org.apache.camel.dsl.xml.io;

import java.io.IOException;

import org.apache.camel.spi.Resource;
import org.apache.camel.xml.in.ModelParser;
import org.apache.camel.xml.io.XmlPullParserException;

/**
 * XML {@link ModelParser} that supports loading:
 * <ul>
 * <li>Standard Camel XML DSL</li>
 * <li>Classic Spring XML <beans> with embedded <camelContext> (limited parsing, to discover <routes> inside
 * <camelContext>)</li>
 * <li>Legacy OSGi <blueprint> with embedded <camelContext> (limited parsing, to discover <routes> inside
 * <camelContext>)</li>
 * </ul>
 */
public class XmlModelParser extends ModelParser {

    private static final String SPRING_NS = "http://camel.apache.org/schema/spring";
    private static final String BLUEPRINT_NS = "http://camel.apache.org/schema/blueprint";

    public XmlModelParser(Resource input, String namespace) throws IOException, XmlPullParserException {
        super(input, namespace);
        addSecondaryNamespace(SPRING_NS);
        addSecondaryNamespace(BLUEPRINT_NS);
    }

    @Override
    protected boolean handleUnexpectedElement(String namespace, String name) throws XmlPullParserException {
        if (isWithinCamelContext(namespace, name) || isAriesBlueprint(namespace)) {
            return true;
        }
        return super.handleUnexpectedElement(namespace, name);
    }

    @Override
    protected boolean ignoreUnexpectedElement(String namespace, String name) throws XmlPullParserException {
        if (isWithinCamelContext(namespace, name) || isAriesBlueprint(namespace)) {
            return true;
        }
        return super.ignoreUnexpectedElement(namespace, name);
    }

    private boolean isAriesBlueprint(String namespace) {
        if (namespace != null && namespace.startsWith("http://aries.apache.org/blueprint/")) {
            return true;
        }
        return false;
    }

    private boolean isWithinCamelContext(String namespace, String name) {
        // accept embedded <camelContext> inside Spring XML <beans> files or OSGi <blueprint> files,
        // so we can discover embedded <routes> inside this <camelContext>.
        if ("camelContext".equals(name) && (SPRING_NS.equals(namespace) || BLUEPRINT_NS.equals(namespace))) {
            return true;
        }
        String[] stack = parser.getNames();
        for (String s : stack) {
            if ("camelContext".equals(s) && (SPRING_NS.equals(namespace) || BLUEPRINT_NS.equals(namespace))) {
                return true;
            }
        }
        return false;
    }
}
