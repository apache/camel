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
package org.apache.camel.util.function;

/**
 * Represents an operation that accepts three input arguments and returns no result and may thrown an exception.
 *
 * @param <I1> the type of the first argument to the operation
 * @param <I2> the type of the second argument to the operation
 * @param <I3> the type of the third argument to the operation
 * @param <T>  the type of the exception the accept method may throw
 */
@FunctionalInterface
public interface ThrowingTriConsumer<I1, I2, I3, T extends Throwable> {
    /**
     * Applies this function to the given arguments, potentially throwing an exception.
     *
     * @param  i1 the first argument
     * @param  i2 the second argument
     * @param  i3 the third argument
     * @return    the function result
     * @throws T  the exception that may be thrown
     */
    void accept(I1 i1, I2 i2, I3 i3) throws T;
}
