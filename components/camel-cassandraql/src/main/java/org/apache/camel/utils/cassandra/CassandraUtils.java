/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.utils.cassandra;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;

/**
 *
 */
public class CassandraUtils {
    /**
     * Apply consistency level if provided, else leave default.
     */
    public static PreparedStatement applyConsistencyLevel(PreparedStatement statement, ConsistencyLevel consistencyLevel) {
        if (consistencyLevel!=null) {
            statement.setConsistencyLevel(consistencyLevel);
        }
        return statement;
    }
    /**
     * Concatenate 2 arrays.
     */
    public static Object[] concat(Object[] array1, Object[] array2) {
        Object[] array = new Object[array1.length+array2.length];
        System.arraycopy(array1, 0, array, 0, array1.length);
        System.arraycopy(array2, 0, array, array1.length, array2.length);
        return array;
    }
    /**
     * Append values to given array.
     */
    public static Object[] append(Object[] array1, Object ... array2) {
        return concat(array1, array2);
    }

}
