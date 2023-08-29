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
 * XML {@link ModelParser} that supports loading classic Spring XML <beans> with embedded <camelContext>, with limited
 * parsing, to discover <routes> inside <camelContext>.
 */
public class XmlModelParser extends ModelParser {

    private static final String SPRING_NS = "http://camel.apache.org/schema/spring";

    public XmlModelParser(Resource input, String namespace) throws IOException, XmlPullParserException {
        super(input, namespace);
        addSecondNamespace(SPRING_NS);
    }

    @Override
    protected boolean handleUnexpectedElement(String namespace, String name) throws XmlPullParserException {
        if ("camelContext".equals(name) && SPRING_NS.equals(namespace)) {
            return true;
        }
        return super.handleUnexpectedElement(namespace, name);
    }
}
