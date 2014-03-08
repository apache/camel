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
package org.apache.camel.spring.handler;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.core.Conventions;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A base class for a parser for a bean.
 *
 * @version 
 */
public class BeanDefinitionParser extends AbstractSingleBeanDefinitionParser {
    private final Class<?> type;
    private final boolean assignId;

    /**
     * Bean definition parser
     *
     * @param type     the type, can be null
     * @param assignId whether to allow assigning id from the id attribute on the type
     *                 (there must be getter/setter id on type class).
     */
    public BeanDefinitionParser(Class<?> type, boolean assignId) {
        this.type = type;
        this.assignId = assignId;
    }

    protected Class<?> getBeanClass(Element element) {
        return type;
    }

    protected boolean isAssignId() {
        return assignId;
    }

    protected boolean isEligibleAttribute(String attributeName) {
        return attributeName != null && !ID_ATTRIBUTE.equals(attributeName)
                && !attributeName.equals("xmlns") && !attributeName.startsWith("xmlns:");
    }

    protected void doParse(Element element, BeanDefinitionBuilder builder) {
        NamedNodeMap attributes = element.getAttributes();
        for (int x = 0; x < attributes.getLength(); x++) {
            Attr attribute = (Attr) attributes.item(x);
            String name = attribute.getLocalName();
            String fullName = attribute.getName();
            // assign id if we want them
            if (fullName.equals("id") && isAssignId()) {
                if (attribute.getValue() != null) {
                    builder.addPropertyValue("id", attribute.getValue());
                }
            // assign other attributes if eligible
            } else if (!fullName.startsWith("xmlns:") && !fullName.equals("xmlns") && isEligibleAttribute(name)) {
                String propertyName = extractPropertyName(name);
                Assert.state(StringUtils.hasText(propertyName),
                        "Illegal property name returned from 'extractPropertyName(String)': cannot be null or empty.");
                builder.addPropertyValue(propertyName, attribute.getValue());
            }
        }
        postProcess(builder, element);
    }


    /**
     * Extract a JavaBean property name from the supplied attribute name.
     * <p>The default implementation uses the
     * {@link Conventions#attributeNameToPropertyName(String)}
     * method to perform the extraction.
     * <p>The name returned must obey the standard JavaBean property name
     * conventions. For example for a class with a setter method
     * '<code>setBingoHallFavourite(String)</code>', the name returned had
     * better be '<code>bingoHallFavourite</code>' (with that exact casing).
     *
     * @param attributeName the attribute name taken straight from the
     *                      XML element being parsed (never <code>null</code>)
     * @return the extracted JavaBean property name (must never be <code>null</code>)
     */
    protected String extractPropertyName(String attributeName) {
        return Conventions.attributeNameToPropertyName(attributeName);
    }

    /**
     * Hook method that derived classes can implement to inspect/change a
     * bean definition after parsing is complete.
     * <p>The default implementation does nothing.
     *
     * @param beanDefinition the parsed (and probably totally defined) bean definition being built
     * @param element        the XML element that was the source of the bean definition's metadata
     */
    protected void postProcess(BeanDefinitionBuilder beanDefinition, Element element) {
    }

}
