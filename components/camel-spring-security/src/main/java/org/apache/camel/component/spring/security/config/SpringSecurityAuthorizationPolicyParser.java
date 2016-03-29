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
package org.apache.camel.component.spring.security.config;

import org.w3c.dom.Element;

import org.apache.camel.component.spring.security.SpringSecurityAccessPolicy;
import org.apache.camel.component.spring.security.SpringSecurityAuthorizationPolicy;
import org.apache.camel.util.ObjectHelper;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;

public class SpringSecurityAuthorizationPolicyParser extends BeanDefinitionParser {

    public SpringSecurityAuthorizationPolicyParser() {
        // true = allow id to be set as there is a setter method on target bean
        super(SpringSecurityAuthorizationPolicy.class, true);
    }
    
    protected boolean isEligibleAttribute(String attributeName) {
        if ("access".equals(attributeName) || "accessDecisionManager".equals(attributeName)
            || "authenticationManager".equals(attributeName)) {
            return false;
        } else {
            return super.isEligibleAttribute(attributeName);
        }
    }
    
    protected void postProcess(BeanDefinitionBuilder builder, Element element) {
        setReferenceIfAttributeDefine(builder, element, "accessDecisionManager");
        setReferenceIfAttributeDefine(builder, element, "authenticationManager");
        if (ObjectHelper.isNotEmpty(element.getAttribute("authenticationAdapter"))) {
            builder.addPropertyReference("authenticationAdapter", element.getAttribute("authenticationAdapter"));
        }

        BeanDefinitionBuilder accessPolicyBuilder = BeanDefinitionBuilder.genericBeanDefinition(
            SpringSecurityAccessPolicy.class.getCanonicalName());
        accessPolicyBuilder.addConstructorArgValue(element.getAttribute("access"));
        builder.addPropertyValue("springSecurityAccessPolicy", accessPolicyBuilder.getBeanDefinition());
    }
    
    protected void setReferenceIfAttributeDefine(BeanDefinitionBuilder builder, Element element, String attribute) {
        String valueRef = attribute;
        if (ObjectHelper.isNotEmpty(element.getAttribute(attribute))) {
            valueRef = element.getAttribute(attribute);
        }
        builder.addPropertyReference(attribute, valueRef);
    }

}
