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
package org.apache.camel.component.twitter;

import java.time.LocalDateTime;

import org.apache.camel.component.twitter.util.TwitterConverter;
import org.junit.jupiter.api.Test;
import twitter4j.v1.Status;
import twitter4j.v1.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TwitterConverterTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Test
    void statusConvertsToTextWithTheScreenName() {
        User user = mock(User.class);
        when(user.getScreenName()).thenReturn("camel");
        Status status = status("Hello");
        when(status.getUser()).thenReturn(user);

        assertEquals(CREATED + " (camel) Hello", TwitterConverter.toString(status));
    }

    @Test
    void statusWithoutUserConvertsToText() {
        assertEquals(CREATED + " Hello", TwitterConverter.toString(status("Hello")));
    }

    private static Status status(String text) {
        Status status = mock(Status.class);
        when(status.getCreatedAt()).thenReturn(CREATED);
        when(status.getText()).thenReturn(text);
        return status;
    }
}
