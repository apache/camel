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
package org.apache.camel.component.cxf.spring;

import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;
import org.w3c.dom.Element;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;


public class CxfEndpointBeanDefinitionParser extends AbstractCxfBeanDefinitionParser {

    @Override
    protected Class<?> getBeanClass(Element arg0) {
        return CxfSpringEndpointBean.class;
    }

    @Override
    protected void mapAttribute(BeanDefinitionBuilder bean, Element e, String name, String val) {
        if ("endpointName".equals(name) || "serviceName".equals(name)) {
            QName q = parseQName(e, val);
            bean.addPropertyValue(name, q);
        } else {
            mapToProperty(bean, name, val);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void mapElement(ParserContext ctx, BeanDefinitionBuilder bean, Element el, String name) {
        if ("properties".equals(name)) {
            Map map = ctx.getDelegate().parseMapElement(el, bean.getBeanDefinition());
            Map props = getPropertyMap(bean, false);
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
            List<?> list = (List<?>)ctx.getDelegate().parseListElement(el, bean.getBeanDefinition());
            bean.addPropertyValue(name, list);
        } else {
            setFirstChildAsProperty(el, ctx, bean, name);
        }
    }

}
