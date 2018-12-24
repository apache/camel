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
package org.apache.camel.reifier.transformer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.model.transformer.CustomTransformerDefinition;
import org.apache.camel.model.transformer.DataFormatTransformerDefinition;
import org.apache.camel.model.transformer.EndpointTransformerDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.spi.Transformer;

public abstract class TransformerReifier<T> {

    private static final Map<Class<?>, Function<TransformerDefinition, TransformerReifier<? extends TransformerDefinition>>> TRANSFORMERS;
    static {
        Map<Class<?>, Function<TransformerDefinition, TransformerReifier<? extends TransformerDefinition>>> map = new HashMap<>();
        map.put(CustomTransformerDefinition.class, CustomTransformeReifier::new);
        map.put(DataFormatTransformerDefinition.class, DataFormatTransformeReifier::new);
        map.put(EndpointTransformerDefinition.class, EndpointTransformeReifier::new);
        TRANSFORMERS = map;
    }
    
    protected final T definition;

    public TransformerReifier(T definition) {
        this.definition = definition;
    }

    public static TransformerReifier<? extends TransformerDefinition> reifier(TransformerDefinition definition) {
        Function<TransformerDefinition, TransformerReifier<? extends TransformerDefinition>> reifier = TRANSFORMERS.get(definition.getClass());
        if (reifier != null) {
            return reifier.apply(definition);
        }
        throw new IllegalStateException("Unsupported definition: " + definition);
    }

    public Transformer createTransformer(CamelContext context) throws Exception {
        return doCreateTransformer(context);
    };

    protected abstract Transformer doCreateTransformer(CamelContext context) throws Exception;

}
