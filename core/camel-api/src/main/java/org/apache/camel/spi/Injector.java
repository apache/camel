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

/**
 * A pluggable strategy for creating and possibly dependency injecting objects
 * which could be implemented using straight forward reflection or using Spring
 * or CDI to perform dependency injection.
 */
public interface Injector {

    /**
     * Instantiates a new instance of the given type; possibly injecting values
     * into the object in the process (bean post processing)
     * 
     * @param type the type of object to create
     * @return a newly created instance
     */
    <T> T newInstance(Class<T> type);

    /**
     * Instantiates a new instance of the given type by using the factory method
     * (this will not perform bean post processing)
     *
     * @param type the type of object to create
     * @param factoryMethod to create the new instance via factory method which must be public static and return the type
     * @return a newly created instance
     */
    <T> T newInstance(Class<T> type, String factoryMethod);

    /**
     * Instantiates a new instance of the given type; possibly injecting values
     * into the object in the process (bean post processing if enabled)
     *
     * @param type the type of object to create
     * @param postProcessBean whether to post process the bean
     * @return a newly created instance
     */
    <T> T newInstance(Class<T> type, boolean postProcessBean);

    /**
     * Whether the injector supports creating new instances using auto-wiring.
     * If this is possible then bean instances is attempt first to be created this way
     * and if not, then the bean can only be created if there is a public no-arg constructor.
     */
    boolean supportsAutoWiring();

}
