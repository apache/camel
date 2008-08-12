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
package org.apache.camel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Subscribes a method to an {@link Endpoint} either via its
 * <a href="http://activemq.apache.org/camel/uris.html">URI</a> or via the name of the endpoint reference
 * which is then resolved in a registry such as the Spring Application Context.
 *
 * When a message {@link Exchange} is received from the {@link Endpoint} then the
 * <a href="http://activemq.apache.org/camel/bean-integration.html">Bean Integration</a>
 * mechanism is used to map the incoming {@link Message} to the method parameters.
 * 
 * @version $Revision$
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR })
public @interface Consume {
    String uri() default "";

    String ref() default "";
}
