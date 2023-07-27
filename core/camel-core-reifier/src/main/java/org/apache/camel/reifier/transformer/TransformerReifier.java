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

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.camel.CamelContext;
import org.apache.camel.model.transformer.CustomTransformerDefinition;
import org.apache.camel.model.transformer.DataFormatTransformerDefinition;
import org.apache.camel.model.transformer.EndpointTransformerDefinition;
import org.apache.camel.model.transformer.LoadTransformerDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.reifier.AbstractReifier;
import org.apache.camel.spi.ReifierStrategy;
import org.apache.camel.spi.Transformer;

public abstract class TransformerReifier<T> extends AbstractReifier {

    // for custom reifiers
    private static final Map<Class<?>, BiFunction<CamelContext, TransformerDefinition, TransformerReifier<? extends TransformerDefinition>>> TRANSFORMERS
            = new HashMap<>(0);

    protected final T definition;

    public TransformerReifier(CamelContext camelContext, T definition) {
        super(camelContext);
        this.definition = definition;
    }

    public static void registerReifier(
            Class<?> processorClass,
            BiFunction<CamelContext, TransformerDefinition, TransformerReifier<? extends TransformerDefinition>> creator) {
        if (TRANSFORMERS.isEmpty()) {
            ReifierStrategy.addReifierClearer(TransformerReifier::clearReifiers);
        }
        TRANSFORMERS.put(processorClass, creator);
    }

    public static TransformerReifier<? extends TransformerDefinition> reifier(
            CamelContext camelContext, TransformerDefinition definition) {

        TransformerReifier<? extends TransformerDefinition> answer = null;
        if (!TRANSFORMERS.isEmpty()) {
            // custom take precedence
            BiFunction<CamelContext, TransformerDefinition, TransformerReifier<? extends TransformerDefinition>> reifier
                    = TRANSFORMERS.get(definition.getClass());
            if (reifier != null) {
                answer = reifier.apply(camelContext, definition);
            }
        }
        if (answer == null) {
            answer = coreReifier(camelContext, definition);
        }
        if (answer == null) {
            throw new IllegalStateException("Unsupported definition: " + definition);
        }
        return answer;
    }

    private static TransformerReifier<? extends TransformerDefinition> coreReifier(
            CamelContext camelContext, TransformerDefinition definition) {
        if (definition instanceof CustomTransformerDefinition) {
            return new CustomTransformerReifier(camelContext, definition);
        } else if (definition instanceof DataFormatTransformerDefinition) {
            return new DataFormatTransformerReifier(camelContext, definition);
        } else if (definition instanceof EndpointTransformerDefinition) {
            return new EndpointTransformerReifier(camelContext, definition);
        } else if (definition instanceof LoadTransformerDefinition) {
            return new LoadTransformerReifier(camelContext, definition);
        }
        return null;
    }

    public static void clearReifiers() {
        TRANSFORMERS.clear();
    }

    public Transformer createTransformer() {
        return doCreateTransformer();
    }

    protected abstract Transformer doCreateTransformer();

}
