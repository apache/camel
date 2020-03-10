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
package org.apache.camel.reifier.transformer;

import org.apache.camel.CamelContext;
import org.apache.camel.model.transformer.CustomTransformerDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.spi.Transformer;

public class CustomTransformeReifier extends TransformerReifier<CustomTransformerDefinition> {

    public CustomTransformeReifier(CamelContext camelContext, TransformerDefinition definition) {
        super(camelContext, (CustomTransformerDefinition)definition);
    }

    @Override
    protected Transformer doCreateTransformer() {
        if (definition.getRef() == null && definition.getClassName() == null) {
            throw new IllegalArgumentException("'ref' or 'className' must be specified for customTransformer");
        }
        Transformer transformer;
        if (definition.getRef() != null) {
            transformer = lookup(parseString(definition.getRef()), Transformer.class);
            if (transformer == null) {
                throw new IllegalArgumentException("Cannot find transformer with ref:" + definition.getRef());
            }
            if (transformer.getModel() != null || transformer.getFrom() != null || transformer.getTo() != null) {
                throw new IllegalArgumentException(String.format("Transformer '%s' is already in use. Please check if duplicate transformer exists.", definition.getRef()));
            }
        } else {
            Class<Transformer> transformerClass = camelContext.getClassResolver().resolveClass(definition.getClassName(), Transformer.class);
            if (transformerClass == null) {
                throw new IllegalArgumentException("Cannot find transformer class: " + definition.getClassName());
            }
            transformer = camelContext.getInjector().newInstance(transformerClass, false);
        }
        transformer.setCamelContext(camelContext);
        return transformer.setModel(definition.getScheme()).setFrom(definition.getFromType()).setTo(definition.getToType());
    }

}
