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
package org.apache.camel;

/**
 * Marker interface indicating that a class contains <a href="https://camel.apache.org/manual/type-converter.html">type
 * converter</a> methods annotated with {@link Converter}.
 * <p/>
 * Implementing this interface allows custom converter classes to be registered at runtime with
 * {@link org.apache.camel.spi.TypeConverterRegistry#addTypeConverters(Object)}, making all {@link Converter}-annotated
 * methods available as converters within the current {@link CamelContext}.
 * <p/>
 * At startup, Camel automatically scans the classpath for converter classes; this interface provides a way to register
 * additional converters programmatically without requiring a classpath scan.
 *
 * @see Converter
 * @see org.apache.camel.spi.TypeConverterRegistry
 */
public interface TypeConverters {
}
