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

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;

/**
 * A parser of the various scripting language expressions
 *
 * @version $Revision$
 */
public class ScriptDefinitionParser extends LazyLoadingBeanDefinitionParser {
    private final String scriptEngineName;

    public ScriptDefinitionParser(String scriptEngineName) {
        super("org.apache.camel.builder.script.ScriptBuilder", "camel-script");
        this.scriptEngineName = scriptEngineName;
    }

    @Override
    protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
        // lets create a child context
        String engine = scriptEngineName;
        if (engine == null) {
            engine = element.getAttribute("language");
        }
        builder.addConstructorArgValue(engine);
        super.doParse(element, parserContext, builder);
        String scriptText = DomUtils.getTextValue(element).trim();
        if (scriptText.length() > 0) {
            builder.addPropertyValue("scriptText", scriptText);
        }
    }
}
