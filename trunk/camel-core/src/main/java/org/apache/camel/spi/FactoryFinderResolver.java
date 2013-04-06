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
package org.apache.camel.spi;

/**
 * Represents a resolver for {@link FactoryFinder}
 *
 * @version 
 */
public interface FactoryFinderResolver {

    /**
     * Creates a new default factory finder using a default resource path.
     *
     * @param classResolver the class resolver to use
     * @return a factory finder.
     */
    FactoryFinder resolveDefaultFactoryFinder(ClassResolver classResolver);

    /**
     * Creates a new factory finder.
     *
     * @param classResolver the class resolver to use
     * @param resourcePath the resource path as base to lookup files within
     * @return a factory finder.
     */
    FactoryFinder resolveFactoryFinder(ClassResolver classResolver, String resourcePath);

}
