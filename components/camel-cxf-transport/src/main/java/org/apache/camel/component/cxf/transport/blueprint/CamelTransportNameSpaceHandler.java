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
package org.apache.camel.component.cxf.transport.blueprint;

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


public class CamelTransportNameSpaceHandler extends BaseNamespaceHandler {
    private static final Logger LOG = LoggerFactory.getLogger(CamelTransportNameSpaceHandler.class);

    public ComponentMetadata decorate(Node node, ComponentMetadata componentMetadata, ParserContext parserContext) {
        return null;
    }

    @SuppressWarnings("rawtypes")
    public Set<Class> getManagedClasses() {
        return new HashSet<Class>(Arrays.asList(CamelTransportNameSpaceHandler.class));
    }

    public URL getSchemaLocation(String s) {
        if ("http://cxf.apache.org/transports/camel/blueprint".equals(s)) {
            return getClass().getClassLoader().getResource("schema/blueprint/camel.xsd");
        }
        return super.findCoreSchemaLocation(s);
    }

    @Override
    public Metadata parse(Element element, ParserContext context) {
        Metadata answer = null;
        String s = element.getLocalName();
        if ("conduit".equals(s)) {
            LOG.debug("parsing the conduit element");
            answer = new CamelConduitDefinitionParser().parse(element, context);
        }
        if ("destination".equals(s)) {
            LOG.debug("parsing the detination element");
            answer = new CamelDestinationDefinitionParser().parse(element, context);
        }
        return answer;
    }

}
