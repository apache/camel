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
package org.apache.camel.language.csimple;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;

/**
 * CSimple for {@link Expression} or {@link Predicate}.
 */
public interface CSimpleExpression extends Expression, Predicate {

    /**
     * Whether this script is to be used as predicate only.
     */
    boolean isPredicate();

    /**
     * The csimple script as text
     */
    String getText();

    /**
     * To do custom initialization
     *
     * @param context the camel context
     */
    @Override
    default void init(CamelContext context) {
        // noop
    }

}
