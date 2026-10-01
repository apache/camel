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
package org.apache.camel.java.in;

/**
 * The value of a constant the parser cannot read itself: one of a class that is not on its class path, such as a
 * component's header constant ({@code KafkaConstants.KEY}) or a constant of another source file of the project
 * ({@code Application.QUEUE}). The parser asks it before it reports the constant as unresolved.
 * <p/>
 * A tool plugs one in with {@link LwJavaParser#setConstantResolver(ConstantResolver)}: with the Camel catalog it knows
 * every component's header constants, with the project's sources it knows their constants.
 */
@FunctionalInterface
public interface ConstantResolver {

    /**
     * The value of a constant: a String, a number or a boolean; null when not known.
     *
     * @param className the class as the source names it, fully qualified when its imports or package say so
     * @param field     the name of the constant
     */
    Object constant(String className, String field);
}
