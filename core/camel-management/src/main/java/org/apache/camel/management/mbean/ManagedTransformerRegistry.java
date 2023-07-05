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
package org.apache.camel.management.mbean;

import java.util.Collection;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedTransformerRegistryMBean;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerRegistry;

@ManagedResource(description = "Managed TransformerRegistry")
public class ManagedTransformerRegistry extends ManagedService implements ManagedTransformerRegistryMBean {
    private final TransformerRegistry<?> transformerRegistry;

    public ManagedTransformerRegistry(CamelContext context, TransformerRegistry<?> transformerRegistry) {
        super(context, transformerRegistry);
        this.transformerRegistry = transformerRegistry;
    }

    public TransformerRegistry<?> getTransformerRegistry() {
        return transformerRegistry;
    }

    @Override
    public String getSource() {
        return transformerRegistry.toString();
    }

    @Override
    public Integer getDynamicSize() {
        return transformerRegistry.dynamicSize();
    }

    @Override
    public Integer getStaticSize() {
        return transformerRegistry.staticSize();
    }

    @Override
    public Integer getSize() {
        return transformerRegistry.size();
    }

    @Override
    public Integer getMaximumCacheSize() {
        return transformerRegistry.getMaximumCacheSize();
    }

    @Override
    public void purge() {
        transformerRegistry.purge();
    }

    @Override
    public TabularData listTransformers() {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.listTransformersTabularType());
            Collection<Transformer> transformers = transformerRegistry.values();
            for (Transformer transformer : transformers) {
                CompositeType ct = CamelOpenMBeanTypes.listTransformersCompositeType();
                String name = transformer.getName();
                DataType from = transformer.getFrom();
                DataType to = transformer.getTo();
                String desc = transformer.toString();
                boolean fromStatic
                        = name != null ? transformerRegistry.isStatic(name) : transformerRegistry.isStatic(from, to);
                boolean fromDynamic
                        = name != null ? transformerRegistry.isDynamic(name) : transformerRegistry.isDynamic(from, to);

                CompositeData data = new CompositeDataSupport(
                        ct, new String[] { "name", "from", "to", "static", "dynamic", "description" },
                        new Object[] { name, from.toString(), to.toString(), fromStatic, fromDynamic, desc });
                answer.put(data);
            }
            return answer;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

}
