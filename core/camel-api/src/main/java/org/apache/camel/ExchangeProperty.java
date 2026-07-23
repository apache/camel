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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method parameter as a named exchange property when Camel performs
 * <a href="https://camel.apache.org/manual/bean-binding.html">bean binding</a>.
 * <p/>
 * Exchange properties are key-value pairs stored on the {@link Exchange} (not on the {@link Message}), surviving across
 * multiple processing steps for the lifetime of the exchange. They are typically used for internal routing state and
 * correlation data. The {@link #value()} attribute names the property to inject; the value is converted to the declared
 * parameter type via the {@link TypeConverter} infrastructure.
 *
 * @see ExchangeProperties
 * @see Exchange#getProperty(String)
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ ElementType.PARAMETER })
public @interface ExchangeProperty {

    /**
     * Name of the property
     */
    String value();
}
