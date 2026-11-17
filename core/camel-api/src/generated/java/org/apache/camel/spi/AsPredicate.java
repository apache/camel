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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the Camel language/expression should be used as predicate.
 *
 * For example the EIPs which accept expression/languages may use them as either expression or predicate. This
 * annotation is used to mark situation where they should be used as predicate. As by default they are used as
 * expression. An example would be the Filter EIP which uses predicate. And the transform EIP uses an expression. <br>
 * Being able to distinguish between these two situations can be of importance to tooling: the catalog carries the
 * annotation as <code>asPredicate</code> on the option in the model JSON. <br>
 * The Loop EIP is the one EIP the annotation cannot describe, as its expression is a predicate only when
 * <code>doWhile</code> is enabled (otherwise it is the number of iterations), so its expression is not annotated and
 * tooling checks the <code>doWhile</code> option instead.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER })
public @interface AsPredicate {
}
