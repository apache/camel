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
 * Represents a function that accepts two arguments, produces a result and may thrown an exception.
 *
 * @param <I1> the type of the first argument to the operation
 * @param <I2> the type of the second argument to the operation
 * @param <R>  the type of the result of the function
 * @param <T>  the type of the exception the accept method may throw
 *
 * @see        java.util.function.BiFunction
 */
@FunctionalInterface
public interface ThrowingBiFunction<I1, I2, R, T extends Throwable> {
    /**
     * Applies this function to the given arguments, potentially throwing an exception.
     *
     * @param  in1 the first function argument
     * @param  in2 the second function argument
     * @return     the function result
     * @throws T   the exception that may be thrown
     */
    R apply(I1 in1, I2 in2) throws T;
}
