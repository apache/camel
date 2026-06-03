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
 * Marks a method parameter as the entire header map of the inbound {@link Message} when Camel performs
 * <a href="https://camel.apache.org/manual/bean-binding.html">bean binding</a>.
 * <p/>
 * The parameter type should be {@code Map<String, Object>} (or a compatible super-type). Unlike {@link Header}, which
 * injects a single named header, {@code @Headers} gives the method direct access to all headers at once, useful when
 * the set of headers to process is dynamic or unknown at compile time.
 *
 * @see Header
 * @see Body
 * @see Message#getHeaders()
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ ElementType.PARAMETER })
public @interface Headers {
}
