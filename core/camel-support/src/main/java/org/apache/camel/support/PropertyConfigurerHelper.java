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
package org.apache.camel.support;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.apache.camel.util.ObjectHelper;

/**
 * Helper class for dealing with configurers.
 *
 * @see org.apache.camel.spi.PropertyConfigurer
 * @see org.apache.camel.spi.PropertyConfigurerGetter
 */
public final class PropertyConfigurerHelper {

    private PropertyConfigurerHelper() {
    }

    /**
     * Resolves the given configurer.
     *
     * @param context the camel context
     * @param target the target object for which we need a {@link org.apache.camel.spi.PropertyConfigurer}
     * @return the resolved configurer, or <tt>null</tt> if no configurer could be found
     */
    public static GeneratedPropertyConfigurer resolvePropertyConfigurer(CamelContext context, Object target) {
        ObjectHelper.notNull(target, "target");
        ObjectHelper.notNull(context, "context");

        return context.adapt(ExtendedCamelContext.class)
            .getConfigurerResolver()
            .resolvePropertyConfigurer(target.getClass().getSimpleName(), context);
    }

    /**
     * Resolves the given configurer.
     *
     * @param context the camel context
     * @param target the target object for which we need a {@link org.apache.camel.spi.PropertyConfigurer}
     * @param type the specific type of {@link org.apache.camel.spi.PropertyConfigurer}
     * @return the resolved configurer, or <tt>null</tt> if no configurer could be found
     */
    public static <T> T resolvePropertyConfigurer(CamelContext context, Object target, Class<T> type) {
        ObjectHelper.notNull(target, "target");
        ObjectHelper.notNull(context, "context");

        GeneratedPropertyConfigurer configurer = resolvePropertyConfigurer(context, target);
        if (type.isInstance(configurer)) {
            return type.cast(configurer);
        }

        return null;
    }
}
