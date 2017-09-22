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
package org.apache.camel.model.language;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * To use the specified language in Camel expressions or predicates.
 *
 * @version 
 */
@Metadata(label = "language,core", title = "Language")
@XmlRootElement(name = "language")
@XmlAccessorType(XmlAccessType.FIELD)
public class LanguageExpression extends ExpressionDefinition {
    @XmlAttribute(required = true)
    private String language;

    public LanguageExpression() {
    }

    public LanguageExpression(String language, String expression) {
        setLanguage(language);
        setExpression(expression);
    }

    public String getLanguage() {
        return language;
    }

    /**
     * The name of the language to use
     */
    public void setLanguage(String language) {
        this.language = language;
    }
}
