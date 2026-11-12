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
package org.apache.camel.component.wordpress.api;

import org.apache.camel.component.wordpress.api.auth.WordpressBasicAuthentication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class WordpressAPIConfigurationTest {

    @Test
    public void testEqualsForTwoDistinctButEqualInstances() {
        final WordpressAPIConfiguration one = new WordpressAPIConfiguration("http://localhost/wp-json", "2");
        final WordpressAPIConfiguration other = new WordpressAPIConfiguration("http://localhost/wp-json", "2");

        assertEquals(one, other);
        assertEquals(one.hashCode(), other.hashCode());
    }

    @Test
    public void testEqualsIsReflexive() {
        final WordpressAPIConfiguration configuration = new WordpressAPIConfiguration("http://localhost/wp-json", "2");

        assertEquals(configuration, configuration);
    }

    @Test
    public void testNotEqualsWhenAFieldDiffers() {
        final WordpressAPIConfiguration configuration = new WordpressAPIConfiguration("http://localhost/wp-json", "2");

        assertNotEquals(configuration, new WordpressAPIConfiguration("http://example.com/wp-json", "2"));
        assertNotEquals(configuration, new WordpressAPIConfiguration("http://localhost/wp-json", "3"));

        final WordpressAPIConfiguration withAuthentication
                = new WordpressAPIConfiguration("http://localhost/wp-json", "2");
        withAuthentication.setAuthentication(new WordpressBasicAuthentication("user", "password"));
        assertNotEquals(configuration, withAuthentication);
    }

    @Test
    public void testNotEqualsForOtherTypesAndNull() {
        final WordpressAPIConfiguration configuration = new WordpressAPIConfiguration("http://localhost/wp-json", "2");

        assertNotEquals(configuration, "http://localhost/wp-json");
        assertNotEquals(null, configuration);
    }

}
