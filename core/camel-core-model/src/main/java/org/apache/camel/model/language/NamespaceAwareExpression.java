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
package org.apache.camel.model.language;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.support.builder.Namespaces;

/**
 * A useful base class for any expression which may be namespace or XML content aware such as {@link XPathExpression} or
 * {@link XQueryExpression}
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class NamespaceAwareExpression extends SingleInputTypedExpressionDefinition implements NamespaceAware {

    @XmlElement(name = "namespace")
    @Metadata(label = "common")
    private List<PropertyDefinition> namespace;
    @XmlTransient
    private Map<String, String> namespaces;

    protected NamespaceAwareExpression() {
    }

    protected NamespaceAwareExpression(String expression) {
        super(expression);
    }

    protected NamespaceAwareExpression(AbstractNamespaceAwareBuilder<?, ?> builder) {
        super(builder);
        this.namespace = builder.namespace;
        this.namespaces = builder.namespaces;
    }

    @Override
    public Map<String, String> getNamespaces() {
        return getNamespaceAsMap();
    }

    /**
     * Injects the XML Namespaces of prefix -> uri mappings
     *
     * @param namespaces the XML namespaces with the key of prefixes and the value the URIs
     */
    @Override
    public void setNamespaces(Map<String, String> namespaces) {
        this.namespaces = namespaces;
    }

    public List<PropertyDefinition> getNamespace() {
        return namespace;
    }

    /**
     * Injects the XML Namespaces of prefix -> uri mappings
     */
    public void setNamespace(List<PropertyDefinition> namespace) {
        this.namespace = namespace;
    }

    public Map<String, String> getNamespaceAsMap() {
        if (namespaces == null && namespace != null) {
            namespaces = new HashMap<>();
        }
        if (namespace != null) {
            for (PropertyDefinition def : namespace) {
                namespaces.put(def.getKey(), def.getValue());
            }
        }
        return namespaces;
    }

    /**
     * {@code NamespaceAwareBuilder} is the base namespace aware expression builder.
     */
    @XmlTransient
    @SuppressWarnings("unchecked")
    abstract static class AbstractNamespaceAwareBuilder<
            T extends AbstractNamespaceAwareBuilder<T, E>, E extends NamespaceAwareExpression>
            extends AbstractBuilder<T, E> {

        private List<PropertyDefinition> namespace;
        private Map<String, String> namespaces;

        /**
         * Injects the XML Namespaces of prefix -> uri mappings
         *
         * @param namespaces the XML namespaces
         */
        public T namespaces(Namespaces namespaces) {
            this.namespaces = namespaces.getNamespaces();
            return (T) this;
        }

        /**
         * Injects the XML Namespaces of prefix -> uri mappings
         *
         * @param namespaces the XML namespaces with the key of prefixes and the value the URIs
         */
        public T namespaces(Map<String, String> namespaces) {
            this.namespaces = namespaces;
            return (T) this;
        }

        /**
         * Injects the XML Namespaces of prefix -> uri mappings
         */
        public T namespace(List<PropertyDefinition> namespace) {
            this.namespace = namespace;
            return (T) this;
        }
    }
}
