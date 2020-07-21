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
package org.apache.camel.impl.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.transformer.TransformerKey;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Default implementation of {@link org.apache.camel.spi.TransformerRegistry}.
 */
public class DefaultTransformerRegistry extends AbstractDynamicRegistry<TransformerKey, Transformer> implements TransformerRegistry<TransformerKey> {

    private final Map<TransformerKey, TransformerKey> aliasMap;

    public DefaultTransformerRegistry(CamelContext context) {
        super(context, CamelContextHelper.getMaximumTransformerCacheSize(context));
        this.aliasMap = new ConcurrentHashMap<>();
    }

    @Override
    public Transformer resolveTransformer(TransformerKey key) {
        if (ObjectHelper.isEmpty(key.getScheme()) && key.getTo() == null) {
            return null;
        }
        
        // try exact match
        Transformer answer = get(aliasMap.getOrDefault(key, key));
        if (answer != null || ObjectHelper.isNotEmpty(key.getScheme())) {
            return answer;
        }
        
        // try wildcard match for next - add an alias if matched
        TransformerKey alias = null;
        if (key.getFrom() != null && ObjectHelper.isNotEmpty(key.getFrom().getName())) {
            alias = new TransformerKey(new DataType(key.getFrom().getModel()), key.getTo());
            answer = get(alias);
        }
        if (answer == null && ObjectHelper.isNotEmpty(key.getTo().getName())) {
            alias = new TransformerKey(key.getFrom(), new DataType(key.getTo().getModel()));
            answer = get(alias);
        }
        if (answer == null && key.getFrom() != null && ObjectHelper.isNotEmpty(key.getFrom().getName())
            && ObjectHelper.isNotEmpty(key.getTo().getName())) {
            alias = new TransformerKey(new DataType(key.getFrom().getModel()), new DataType(key.getTo().getModel()));
            answer = get(alias);
        }
        if (answer == null && key.getFrom() != null) {
            alias = new TransformerKey(key.getFrom().getModel());
            answer = get(alias);
        }
        if (answer == null) {
            alias = new TransformerKey(key.getTo().getModel());
            answer = get(alias);
        }
        if (answer != null) {
            aliasMap.put(key, alias);
        }
        
        return answer;
    }

    @Override
    public Transformer put(TransformerKey key, Transformer transformer) {
        // ensure transformer is started before its being used
        ServiceHelper.startService(transformer);
        return super.put(key, transformer);
    }

    @Override
    public boolean isStatic(String scheme) {
        return isStatic(new TransformerKey(scheme));
    }

    @Override
    public boolean isStatic(DataType from, DataType to) {
        return isStatic(new TransformerKey(from, to));
    }

    @Override
    public boolean isDynamic(String scheme) {
        return isDynamic(new TransformerKey(scheme));
    }

    @Override
    public boolean isDynamic(DataType from, DataType to) {
        return isDynamic(new TransformerKey(from, to));
    }

    @Override
    public String toString() {
        return "TransformerRegistry for " + context.getName() + ", capacity: " + maxCacheSize;
    }

}
