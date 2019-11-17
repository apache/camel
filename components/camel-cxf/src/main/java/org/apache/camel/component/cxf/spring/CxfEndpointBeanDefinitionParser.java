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
package org.apache.camel.component.cxf.spring;

import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.w3c.dom.Element;

import org.apache.camel.component.cxf.CxfSpringEndpoint;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.helpers.CastUtils;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;

public class CxfEndpointBeanDefinitionParser extends AbstractCxfBeanDefinitionParser {

    @Override
    protected Class<?> getBeanClass(Element arg0) {
        return CxfSpringEndpoint.class;
    }

    private boolean isSpringPlaceHolder(String value) {
        if (value != null && (value.startsWith("${") && value.endsWith("}"))
                || value.startsWith("{{") && value.endsWith("}}")) {
            return true;
        }
        return false;
    }

    @Override
    protected boolean parseAttributes(Element element, ParserContext ctx, BeanDefinitionBuilder bean) {
        boolean addedBus = super.parseAttributes(element, ctx, bean);
        final String bus = element.getAttribute("bus");
        if (!addedBus && !StringUtils.isEmpty(bus)) {
            bean.addPropertyReference("bus", bus.startsWith("#") ? bus.substring(1) : bus);
            addedBus = true;
        }
        return addedBus;
    }

    @Override
    protected void mapAttribute(BeanDefinitionBuilder bean, Element e, String name, String val) {
        if ("endpointName".equals(name) || "serviceName".equals(name)) {
            if (isSpringPlaceHolder(val)) {
                // set the property with the String value directly
                mapToProperty(bean, name, val);
            } else {
                QName q = parseQName(e, val);
                bean.addPropertyValue(name + "AsQName", q);
            }
        } else {
            mapToProperty(bean, name, val);
        }
    }

    @Override
    protected void mapElement(ParserContext ctx, BeanDefinitionBuilder bean, Element el, String name) {
        if ("properties".equals(name)) {
            Map<String, Object> map = CastUtils.cast(ctx.getDelegate().parseMapElement(el, bean.getBeanDefinition()));
            Map<String, Object> props = getPropertyMap(bean, false);
            if (props != null) {
                map.putAll(props);
            }
            bean.addPropertyValue("properties", map);
        } else if ("binding".equals(name)) {
            setFirstChildAsProperty(el, ctx, bean, "bindingConfig");
        } else if ("inInterceptors".equals(name) || "inFaultInterceptors".equals(name)
            || "outInterceptors".equals(name) || "outFaultInterceptors".equals(name)
            || "features".equals(name) || "schemaLocations".equals(name)
            || "handlers".equals(name)) {
            List<?> list = ctx.getDelegate().parseListElement(el, bean.getBeanDefinition());
            bean.addPropertyValue(name, list);
        } else {
            setFirstChildAsProperty(el, ctx, bean, name);
        }
    }

}
