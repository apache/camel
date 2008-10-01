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
 * Indicates that this method is to be used as a 
 * <a href="http://activemq.apache.org/camel/recipient-list.html">Dynamic Recipient List</a> routing the incoming message
 * to one or more endpoints.
 *
 * When a message {@link org.apache.camel.Exchange} is received from an {@link org.apache.camel.Endpoint} then the
 * <a href="http://activemq.apache.org/camel/bean-integration.html">Bean Integration</a>
 * mechanism is used to map the incoming {@link org.apache.camel.Message} to the method parameters.
 *
 * The return value of the method is then converted to either a {@link java.util.Collection} or array of objects where each
 * element is converted to an {@link Endpoint} or a {@link String}, or if it is not a collection/array then it is converted
 * to an {@link Endpoint} or {@link String}.
 *
 * Then for each endpoint or URI the message is forwarded a separate copy.
 *
 * @version $Revision$
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR })
public @interface RecipientList {
}