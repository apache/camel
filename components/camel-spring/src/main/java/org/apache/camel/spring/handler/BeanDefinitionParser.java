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

import org.w3c.dom.Element;

import org.springframework.beans.factory.xml.AbstractSimpleBeanDefinitionParser;

/**
 * A base class for a parser for a bean.
 *
 * @version $Revision: 1.1 $
 */
public class BeanDefinitionParser extends AbstractSimpleBeanDefinitionParser {
    private Class type;

    protected BeanDefinitionParser() {
    }

    public BeanDefinitionParser(Class type) {
        this.type = type;
    }

    protected Class getBeanClass(Element element) {
        if (type == null) {
            type = loadType();
        }
        return type;
    }

    protected Class loadType() {
        throw new IllegalArgumentException("No type specified!");
    }

    @Override
    protected boolean isEligibleAttribute(String attributeName) {
        return attributeName != null && super.isEligibleAttribute(attributeName) && !attributeName.equals("xmlns");
    }
}