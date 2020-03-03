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
package org.apache.camel.xml.in;

import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.spi.XMLRoutesDefinitionLoader;
import org.apache.camel.spi.annotations.JdkService;

/**
 * {@link XMLRoutesDefinitionLoader} that uses {@link ModelParser} to load and parse the routes from XML
 * which is fast and light-weight compared to the default that uses JAXB.
 */
@JdkService(XMLRoutesDefinitionLoader.FACTORY)
public class ModelParserXMLRoutesDefinitionLoader implements XMLRoutesDefinitionLoader {

    public static final String NAMESPACE = "http://camel.apache.org/schema/spring";

    @Override
    public Object loadRoutesDefinition(CamelContext context, InputStream inputStream) throws Exception {
        ModelParser parser = new ModelParser(inputStream, NAMESPACE);
        return parser.parseRoutesDefinition();
    }

    @Override
    public Object loadRestsDefinition(CamelContext context, InputStream inputStream) throws Exception {
        ModelParser parser = new ModelParser(inputStream, NAMESPACE);
        return parser.parseRestsDefinition();
    }

    @Override
    public <T extends NamedNode> T createModelFromXml(CamelContext context, String xml, Class<T> type) throws Exception {
        throw new UnsupportedOperationException("Not support use camel-xml-jaxb instead");
    }

    @Override
    public String toString() {
        return "camel-xml-io";
    }
}
