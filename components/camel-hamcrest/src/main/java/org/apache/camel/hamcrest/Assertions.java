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
package org.apache.camel.hamcrest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

/**
 * A set of useful assertions you can use when testing
 *
 * @version $Revision$
 */
public final class Assertions {
    private Assertions() {
        // Helper class
    }

    /**
     * Performs the assertion that the given value is an instance of the specified type
     *
     * @param value the value to be compared
     * @param type  the type to assert
     * @return the value cast as the type
     * @throws AssertionError if the instance is not of the correct type
     */
    public static <T> T assertInstanceOf(Object value, Class<T> type) {
        assertThat(value, instanceOf(type));
        return type.cast(value);
    }

    /**
     * Performs the assertion that the given value is an instance of the specified type
     *
     * @param message the description of the value
     * @param value   the value to be compared
     * @param type    the type to assert
     * @return the value cast as the type
     * @throws AssertionError if the instance is not of the correct type
     */
    public static <T> T assertInstanceOf(String message, Object value, Class<T> type) {
        assertThat(message, value, instanceOf(type));
        return type.cast(value);
    }
}
