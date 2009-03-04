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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;
import org.w3c.dom.Element;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.BusWiringBeanFactoryPostProcessor;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.configuration.spring.AbstractBeanDefinitionParser;
import org.apache.cxf.service.factory.ReflectionServiceFactoryBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class CxfEndpointBeanDefinitionParser extends AbstractBeanDefinitionParser {

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
    private Map<String, Object> getPropertyMap(BeanDefinitionBuilder bean, boolean lazyInstantiation) {
        PropertyValue propertyValue = (PropertyValue)bean.getBeanDefinition().getPropertyValues()
            .getPropertyValue("properties");
        
        Map<String, Object> map = null;
        if (propertyValue == null) {
            if (lazyInstantiation) {
                map = new HashMap<String, Object>();
                bean.addPropertyValue("properties", map);
            }
        } else {
            map = (Map<String, Object>)propertyValue.getValue();
        }
        return map;
    }
    
    // To make the CxfEndpointBean clear without touching any Spring relates class 
    // , we implements the ApplicationContextAware here
    public static class CxfSpringEndpointBean extends CxfEndpointBean implements ApplicationContextAware {
        private ApplicationContext applicationContext;
        private String beanId;
        
        public CxfSpringEndpointBean() {
            super();
        }
        
        public CxfSpringEndpointBean(ReflectionServiceFactoryBean factory) {
            super(factory);
        }
        
        public void setApplicationContext(ApplicationContext ctx) throws BeansException {
            applicationContext = ctx;
            if (getBus() == null) {
                Bus bus = BusFactory.getThreadDefaultBus();                
                setBus(bus);
            }
            BusWiringBeanFactoryPostProcessor.updateBusReferencesInContext(getBus(), ctx);
        }
        
        public ApplicationContext getApplicationContext() {
            return applicationContext;
        }
        
        public void setId(String id) {
            beanId = id;
        }
        
        public String getId() {
            return beanId;
        }
        
    }


}
