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

import jakarta.xml.bind.annotation.XmlEnum;

/**
 * Policy controlling what happens when a {@link org.apache.camel.spi.TypeConverterRegistry} receives a request to
 * register a <a href="https://camel.apache.org/manual/type-converter.html">type converter</a> whose from-to type pair
 * is already registered.
 * <p/>
 * This policy is set on the registry via
 * {@link org.apache.camel.spi.TypeConverterRegistry#setTypeConverterExists(TypeConverterExists)} and determines whether
 * a duplicate registration replaces the existing converter ({@link #Override}), is silently discarded
 * ({@link #Ignore}), or raises a hard failure ({@link #Fail}). The default behavior is {@link #Ignore}.
 *
 * @see org.apache.camel.spi.TypeConverterRegistry
 * @see TypeConverter
 */
@XmlEnum
public enum TypeConverterExists {

    /** Replace the existing type converter. */
    Override,
    /** Keep the existing type converter and discard the new one. */
    Ignore,
    /** Throw an exception to signal a duplicate type converter. */
    Fail

}
