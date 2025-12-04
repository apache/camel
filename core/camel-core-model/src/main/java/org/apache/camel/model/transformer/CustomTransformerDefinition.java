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

package org.apache.camel.model.transformer;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Transformer;

/**
 * To use a custom transformer on a route level.
 */
@Metadata(label = "transformation")
@XmlRootElement(name = "customTransformer")
@XmlAccessorType(XmlAccessType.FIELD)
public class CustomTransformerDefinition extends TransformerDefinition {

    @XmlAttribute
    private String ref;

    @XmlAttribute
    private String className;

    public CustomTransformerDefinition() {}

    protected CustomTransformerDefinition(CustomTransformerDefinition source) {
        super(source);
        this.ref = source.ref;
        this.className = source.className;
    }

    @Override
    public CustomTransformerDefinition copyDefinition() {
        return new CustomTransformerDefinition(this);
    }

    public String getRef() {
        return ref;
    }

    /**
     * Set a bean reference of the {@link Transformer}
     */
    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getClassName() {
        return className;
    }

    /**
     * Set a class name of the {@link Transformer}
     */
    public void setClassName(String className) {
        this.className = className;
    }
}
