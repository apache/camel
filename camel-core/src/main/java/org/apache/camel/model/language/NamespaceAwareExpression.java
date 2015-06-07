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

import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.NamespaceAware;

/**
 * A useful base class for any expression which may be namespace or XML content aware
 * such as {@link XPathExpression} or {@link XQueryExpression}
 *
 * @version 
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class NamespaceAwareExpression extends ExpressionDefinition implements NamespaceAware {
    @XmlTransient
    private Map<String, String> namespaces;

    public NamespaceAwareExpression() {
    }

    public NamespaceAwareExpression(String expression) {
        super(expression);
    }


    public Map<String, String> getNamespaces() {
        return namespaces;
    }

    /**
     * Injects the XML Namespaces of prefix -> uri mappings
     *
     * @param namespaces the XML namespaces with the key of prefixes and the value the URIs
     */
    public void setNamespaces(Map<String, String> namespaces) {
        this.namespaces = namespaces;
    }

    @Override
    protected void configureExpression(CamelContext camelContext, Expression expression) {
        configureNamespaceAware(expression);
        super.configureExpression(camelContext, expression);
    }

    @Override
    protected void configurePredicate(CamelContext camelContext, Predicate predicate) {
        configureNamespaceAware(predicate);
        super.configurePredicate(camelContext, predicate);
    }

    protected void configureNamespaceAware(Object builder) {
        if (namespaces != null && builder instanceof NamespaceAware) {
            NamespaceAware namespaceAware = (NamespaceAware) builder;
            namespaceAware.setNamespaces(namespaces);
        }
    }
}
