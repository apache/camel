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
package org.apache.camel.component.wordpress.api.model;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class PublishableStatusTest {

    @Test
    public void testFromString() {
        final String input1 = "PRIVATE";
        final String input2 = "private";

        assertThat(PublishableStatus.fromString(input1), is(PublishableStatus.private_));
        assertThat(PublishableStatus.fromString(input2), is(PublishableStatus.private_));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromStringEmpty() {
        final String input3 = "";

        assertThat(PublishableStatus.fromString(input3), is(PublishableStatus.private_));
    }

    @Test(expected = NullPointerException.class)
    public void testFromStringNull() {
        final String input4 = null;

        assertThat(PublishableStatus.fromString(input4), is(PublishableStatus.private_));
    }

}
