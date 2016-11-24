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
package org.apache.camel.model.transformer;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Transformer;

/**
 * Represents a CustomTransformer. One of the bean reference (ref) or fully qualified class name (type)
 * of the custom {@link Transformer} needs to be specified.
 * 
 * {@see TransformerDefinition}
 * {@see Transformer}
 */
@Metadata(label = "transformation")
@XmlType(name = "customTransformer")
@XmlAccessorType(XmlAccessType.FIELD)
public class CustomTransformerDefinition extends TransformerDefinition {
    @XmlAttribute
    private String ref;
    @XmlAttribute
    private String type;

    @Override
    protected Transformer doCreateTransformer(CamelContext context) throws Exception {
        if (ref == null && type == null) {
            throw new IllegalArgumentException("'ref' or 'type' must be specified for customTransformer");
        }
        Transformer transformer;
        if (ref != null) {
            transformer = context.getRegistry().lookupByNameAndType(ref, Transformer.class);
            if (transformer == null) {
                throw new IllegalArgumentException("Cannot find transformer with ref:" + ref);
            }
            if (transformer.getModel() != null || transformer.getFrom() != null || transformer.getTo() != null) {
                throw new IllegalArgumentException(String.format("Transformer '%s' is already in use. Please check if duplicate transformer exists.", ref));
            }
        } else {
            Class<Transformer> transformerClass = context.getClassResolver().resolveMandatoryClass(type, Transformer.class);
            if (transformerClass == null) {
                throw new IllegalArgumentException("Cannot find transformer class: " + type);
            }
            transformer = context.getInjector().newInstance(transformerClass);

        }
        transformer.setCamelContext(context);
        return transformer.setModel(getScheme())
                          .setFrom(getFrom())
                          .setTo(getTo());
    }

    public String getRef() {
        return ref;
    }

    /**
     * Set a bean reference of the Transformer
     * @param ref the bean reference of the Transformer
     */
    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getType() {
        return type;
    }

    /**
     * Set a class name of the Transformer
     * @param ref the class name of the Transformer
     */
    public void setType(String type) {
        this.type = type;
    }

}

