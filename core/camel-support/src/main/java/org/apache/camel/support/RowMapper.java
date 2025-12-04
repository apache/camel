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

package org.apache.camel.support;

/**
 * Maps rows from one type to another. Commonly used for converting database rows, API responses, or structured data
 * into domain objects or maps.
 * <p>
 * Implementations should be stateless and thread-safe when reused across multiple rows.
 *
 * @param <T> the input type (e.g., ResultSet row, FieldValueList)
 * @param <R> the output type (e.g., Map, domain object)
 */
public interface RowMapper<T, R> {

    /**
     * Maps a value from type T to type R.
     *
     * @param  value the input value to be mapped
     * @return       the mapped output value
     */
    R map(T value);
}
