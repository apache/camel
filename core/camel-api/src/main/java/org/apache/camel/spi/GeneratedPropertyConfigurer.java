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
 * Marker for a {@link PropertyConfigurer} that Camel generates at build time for fast configuration of components and
 * endpoints.
 * <p/>
 * Implementations are produced by the Camel tooling from a type's options, providing reflection-free property binding
 * during bootstrap. Application code does not implement this directly; it is generated alongside the component or
 * endpoint it configures.
 * <p/>
 * See <a href="https://camel.apache.org/manual/property-binding.html">Property Binding</a> in the Camel user manual.
 *
 * @see   PropertyConfigurer
 * @since 3.0
 */
public interface GeneratedPropertyConfigurer extends PropertyConfigurer {

}
