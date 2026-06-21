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
package org.apache.camel.spi;

import org.jspecify.annotations.Nullable;

/**
 * Implemented by objects that can supply the {@link PropertyConfigurer} for a given bean instance.
 * <p/>
 * This lets Camel obtain the fast, reflection-free configurer for a target without looking it up separately, for
 * example when a {@link org.apache.camel.Component} provides the configurer for the endpoints it creates. The returned
 * configurer is then used to bind properties during bootstrap.
 * <p/>
 * See <a href="https://camel.apache.org/manual/property-binding.html">Property Binding</a> in the Camel user manual.
 *
 * @see   PropertyConfigurer
 * @since 3.0
 */
public interface PropertyConfigurerAware {

    /**
     * Gets the configurer.
     *
     * @param instance the bean instance
     */
    @Nullable
    PropertyConfigurer getPropertyConfigurer(Object instance);

}
