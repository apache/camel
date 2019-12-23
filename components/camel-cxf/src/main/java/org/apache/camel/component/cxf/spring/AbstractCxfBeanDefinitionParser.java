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

import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Element;

import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.configuration.spring.AbstractBeanDefinitionParser;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;

public abstract class AbstractCxfBeanDefinitionParser extends AbstractBeanDefinitionParser {
    
    /**
     * Override mapToProperty() to handle the '#' reference notation ourselves.  We put those 
     * properties with '#' in property map and let component to invoke setProperties() on the
     * endpoint. 
     */
    @Override
    protected void mapToProperty(BeanDefinitionBuilder bean, String propertyName, String val) {
        if (ID_ATTRIBUTE.equals(propertyName)) {
            return;
        }

        if (org.springframework.util.StringUtils.hasText(val)) {
            if (val.startsWith("#")) {
                Map<String, Object> map = getPropertyMap(bean, true);
                map.put(propertyName, val);
            } else {
                bean.addPropertyValue(propertyName, val);
            }
        }
    }

    @Override
    protected void doParse(Element element, ParserContext ctx, BeanDefinitionBuilder bean) {
        super.doParse(element, ctx, bean);
        bean.setLazyInit(false);
        // put the bean id into the property map
        Map<String, Object> map = getPropertyMap(bean, true);
        map.put("beanId", resolveId(element, bean.getBeanDefinition(), ctx));
        // set the bean scope to be prototype, then we can get a new instance in each look up 
        bean.setScope(BeanDefinition.SCOPE_PROTOTYPE);
    }

    @Override
    protected String resolveId(Element elem,
                               AbstractBeanDefinition definition,
                               ParserContext ctx)
        throws BeanDefinitionStoreException {
        String id = super.resolveId(elem, definition, ctx);        
        
        if (StringUtils.isEmpty(id)) {
            throw new BeanDefinitionStoreException("The bean id is needed.");
        }       
        return id;
    }

    @Override
    protected boolean hasBusProperty() {
        return true;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> getPropertyMap(BeanDefinitionBuilder bean, boolean lazyInstantiation) {
        PropertyValue propertyValue = bean.getBeanDefinition().getPropertyValues().getPropertyValue("properties");
        
        Map<String, Object> map = null;
        if (propertyValue == null) {
            if (lazyInstantiation) {
                map = new HashMap<>();
                bean.addPropertyValue("properties", map);
            }
        } else {
            map = (Map<String, Object>)propertyValue.getValue();
        }
        return map;
    }

}
