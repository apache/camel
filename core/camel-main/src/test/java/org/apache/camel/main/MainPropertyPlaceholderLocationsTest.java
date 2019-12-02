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
package org.apache.camel.main;

import org.junit.Assert;
import org.junit.Test;

public class MainPropertyPlaceholderLocationsTest extends Assert {
    @Test(expected = IllegalArgumentException.class)
    public void testDefaultPropertyPlaceholderLocationDisabled() {
        Main main = new Main();
        try {
            main.setDefaultPropertyPlaceholderLocation("false");
            main.start();
            main.getCamelContext().resolvePropertyPlaceholders("{{hello}}");
        } finally {
            main.stop();
        }
    }

    @Test
    public void testDefaultPropertyPlaceholderLocationEnabled() {
        Main main = new Main();
        try {
            main.start();
            assertEquals("World", main.getCamelContext().resolvePropertyPlaceholders("{{hello}}"));
        } finally {
            main.stop();
        }
    }

    @Test
    public void testCustomPropertyPlaceholderLocationEnabled() {
        Main main = new Main();
        try {
            main.setDefaultPropertyPlaceholderLocation("classpath:default.properties");
            main.start();
            assertEquals("default", main.getCamelContext().resolvePropertyPlaceholders("{{hello}}"));
        } finally {
            main.stop();
        }
    }
}
