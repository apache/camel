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

import java.util.List;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import org.apache.aries.blueprint.ParserContext;
import org.apache.aries.blueprint.mutable.MutableBeanMetadata;
import org.apache.aries.blueprint.mutable.MutablePassThroughMetadata;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.configuration.blueprint.AbstractBPBeanDefinitionParser;
import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.jaxrs.model.UserResource;
import org.apache.cxf.jaxrs.utils.ResourceUtils;
import org.osgi.service.blueprint.reflect.Metadata;

public class RsServerDefinitionParser extends AbstractBeanDefinitionParser {
    
    public Metadata parse(Element element, ParserContext context) {
        MutableBeanMetadata beanMetadata = createBeanMetadata(element, context, RsServerBlueprintBean.class);
        NamedNodeMap atts = element.getAttributes();

        String bus = null;
        for (int i = 0; i < atts.getLength(); i++) {
            Attr node = (Attr) atts.item(i);
            String val = node.getValue();
            String pre = node.getPrefix();
            String name = node.getLocalName();
            if ("bus".equals(name)) {
                bus = val;
            } else if (isAttribute(pre, name)) {
                if ("depends-on".equals(name)) {
                    beanMetadata.addDependsOn(val);
                } else if (!"name".equals(name)) {
                    beanMetadata.addProperty(name, AbstractBPBeanDefinitionParser.createValue(context, val));
                }
            }
        }

        for (Element elem = DOMUtils.getFirstElement(element); elem != null; elem = DOMUtils.getNextElement(elem)) {
            String name = elem.getLocalName();
            if ("properties".equals(name)
                || "extensionMappings".equals(name)
                || "languageMappings".equals(name)) {
                Metadata map = parseMapData(context, beanMetadata, elem);
                beanMetadata.addProperty(name, map);
            } else if ("binding".equals(name)) {
                setFirstChildAsProperty(elem, context, beanMetadata, "bindingConfig");
            } else if ("inInterceptors".equals(name) || "inFaultInterceptors".equals(name) || "outInterceptors".equals(name)
                || "outFaultInterceptors".equals(name) || "features".equals(name) || "schemaLocations".equals(name) || "handlers".equals(name)) {
                Metadata list = parseListData(context, beanMetadata, elem);
                beanMetadata.addProperty(name, list);
            } else if ("features".equals(name) || "providers".equals(name)
                || "schemaLocations".equals(name) || "modelBeans".equals(name)
                || "serviceBeans".equals(name)) {
                Metadata list = parseListData(context, beanMetadata, elem);
                beanMetadata.addProperty(name, list);
            } else if ("model".equals(name)) {
                List<UserResource> resources = ResourceUtils.getResourcesFromElement(elem);
                MutablePassThroughMetadata value = context.createMetadata(MutablePassThroughMetadata.class);
                value.setObject(resources);
                beanMetadata.addProperty(name, value);
            } else {
                setFirstChildAsProperty(elem, context, beanMetadata, name);
            }
        } 
 
        if (StringUtils.isEmpty(bus)) {
            bus = "cxf";
        }
        //Will create a bus if needed...

        beanMetadata.addProperty("bus", getBusRef(context, bus));
        return beanMetadata;
    }

}
