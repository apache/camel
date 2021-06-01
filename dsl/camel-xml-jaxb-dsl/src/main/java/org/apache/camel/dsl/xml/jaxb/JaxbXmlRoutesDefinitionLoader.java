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
package org.apache.camel.dsl.xml.jaxb;

import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.XMLRoutesDefinitionLoader;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.xml.jaxb.JaxbHelper;

/**
 * JAXB based {@link XMLRoutesDefinitionLoader}. This is the default loader used historically by Camel. The camel-xml-io
 * parser is a light-weight alternative.
 */
@JdkService(XMLRoutesDefinitionLoader.FACTORY)
@Deprecated
public class JaxbXmlRoutesDefinitionLoader implements XMLRoutesDefinitionLoader {

    @Override
    public Object loadRoutesDefinition(CamelContext context, InputStream inputStream) throws Exception {
        return JaxbHelper.loadRoutesDefinition(context, inputStream);
    }

    @Override
    public Object loadRouteTemplatesDefinition(CamelContext context, InputStream inputStream) throws Exception {
        return JaxbHelper.loadRouteTemplatesDefinition(context, inputStream);
    }

    @Override
    public Object loadRestsDefinition(CamelContext context, InputStream inputStream) throws Exception {
        return JaxbHelper.loadRestsDefinition(context, inputStream);
    }

    @Override
    public String toString() {
        return "camel-xml-jaxb";
    }

}
