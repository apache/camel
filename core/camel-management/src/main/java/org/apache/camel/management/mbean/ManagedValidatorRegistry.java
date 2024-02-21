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
import org.apache.camel.api.management.mbean.ManagedValidatorRegistryMBean;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Validator;
import org.apache.camel.spi.ValidatorRegistry;

@ManagedResource(description = "Managed ValidatorRegistry")
public class ManagedValidatorRegistry extends ManagedService implements ManagedValidatorRegistryMBean {
    private final ValidatorRegistry<?> validatorRegistry;

    public ManagedValidatorRegistry(CamelContext context, ValidatorRegistry<?> validatorRegistry) {
        super(context, validatorRegistry);
        this.validatorRegistry = validatorRegistry;
    }

    public ValidatorRegistry<?> getValidatorRegistry() {
        return validatorRegistry;
    }

    @Override
    public String getSource() {
        return validatorRegistry.toString();
    }

    @Override
    public Integer getDynamicSize() {
        return validatorRegistry.dynamicSize();
    }

    @Override
    public Integer getStaticSize() {
        return validatorRegistry.staticSize();
    }

    @Override
    public Integer getSize() {
        return validatorRegistry.size();
    }

    @Override
    public Integer getMaximumCacheSize() {
        return validatorRegistry.getMaximumCacheSize();
    }

    @Override
    public void purge() {
        validatorRegistry.purge();
    }

    @Override
    public TabularData listValidators() {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.listValidatorsTabularType());
            Collection<Validator> validators = validatorRegistry.values();
            for (Validator validator : validators) {
                CompositeType ct = CamelOpenMBeanTypes.listValidatorsCompositeType();
                DataType type = validator.getType();
                String desc = validator.toString();
                boolean isStatic = validatorRegistry.isStatic(type);
                boolean isDynamic = validatorRegistry.isDynamic(type);

                CompositeData data = new CompositeDataSupport(
                        ct, new String[] { "type", "static", "dynamic", "description" },
                        new Object[] { type.toString(), isStatic, isDynamic, desc });
                answer.put(data);
            }
            return answer;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

}
