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

import org.apache.aries.blueprint.ParserContext;
import org.apache.cxf.helpers.BaseNamespaceHandler;
import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.osgi.service.blueprint.reflect.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class CxfNamespaceHandler extends BaseNamespaceHandler {
    private static final Logger LOG = LoggerFactory.getLogger(CxfNamespaceHandler.class);

    public URL getSchemaLocation(String s) {
        if ("http://camel.apache.org/schema/blueprint/cxf".equals(s)) {
            return getClass().getClassLoader().getResource("schema/blueprint/camel-cxf.xsd");
        }
        return super.findCoreSchemaLocation(s);
    }

    @SuppressWarnings({"rawtypes"})
    public Set<Class> getManagedClasses() {
        return new HashSet<Class>(Arrays.asList(CxfNamespaceHandler.class));
    }

    public Metadata parse(Element element, ParserContext context) {
        Metadata answer = null;
        String s = element.getLocalName();
        if ("cxfEndpoint".equals(s)) {
            LOG.debug("parsing the cxfEndpoint element");
            answer = new EndpointDefinitionParser().parse(element, context);
        }
        if ("rsClient".equals(s)) {
            LOG.debug("parsing the rsClient element");
            answer = new RsClientDefinitionParser().parse(element, context);
        }
        if ("rsServer".equals(s)) {
            LOG.debug("parsing the rsServer element");
            answer = new RsServerDefinitionParser().parse(element, context);
        }
        
        return answer;
    }

    public ComponentMetadata decorate(Node node, ComponentMetadata componentMetadata, ParserContext parserContext) {
        return null;
    }
}
