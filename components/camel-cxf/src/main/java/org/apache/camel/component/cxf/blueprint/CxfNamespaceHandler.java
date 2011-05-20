/**
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

package org.apache.camel.component.cxf.blueprint;

import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.apache.aries.blueprint.NamespaceHandler;
import org.apache.aries.blueprint.ParserContext;
import org.apache.cxf.bus.blueprint.BlueprintBus;
import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.osgi.service.blueprint.reflect.Metadata;

public class CxfNamespaceHandler implements NamespaceHandler {

    public URL getSchemaLocation(String s) {
        return getClass().getClassLoader().getResource("schema/blueprint/camel-cxf.xsd");
    }

    @SuppressWarnings("unchecked")
    public Set<Class> getManagedClasses() {
        return new HashSet<Class>(Arrays.asList(CxfNamespaceHandler.class));
    }

    public Metadata parse(Element element, ParserContext context) {
        Thread.currentThread().setContextClassLoader(BlueprintBus.class.getClassLoader());
        String s = element.getLocalName();
        if ("cxfEndpoint".equals(s)) {
            return new EndpointDefinitionParser().parse(element, context);
        } else if ("server".equals(s)) {
            //return new RsServerDefinitionParser(JaxWsServerFactoryBean.class).parse(element, context);
        } else if ("client".equals(s)) {
            //return new RsClientDefinitionParser(JaxWsProxyFactoryBean.class).parse(element, context);
        }
        return null;
    }

    public ComponentMetadata decorate(Node node, ComponentMetadata componentMetadata, ParserContext parserContext) {
        System.out.println("Decorate the node " + node + " " + componentMetadata);
        return null;
    }
}
