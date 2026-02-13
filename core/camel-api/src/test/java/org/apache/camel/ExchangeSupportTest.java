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
package org.apache.camel;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ExchangeSupportTest {

    @Test
    public void getPropertyShouldReturnNullOnMissingProperty() {
        Exchange myExchange = new MockExchange();
        myExchange.setProperty("hello", "world");

        assertNull(myExchange.getProperty("missing", String.class));
        assertNull(myExchange.getPropertyAsCollection("missing", String.class));
        assertNull(myExchange.getPropertyAsList("missing", String.class));
        assertNull(myExchange.getPropertyAsMap("missing", String.class, String.class));
        assertNull(myExchange.getPropertyAsSet("missing", String.class));
        // Optional must not be null, but empty
        assertNotNull(myExchange.getPropertyAsOptional("missing", String.class));
        assertFalse(myExchange.getPropertyAsOptional("missing", String.class).isPresent());
    }

    @Test
    public void getPropertyShouldReturnNullOnClassCastException() {
        Exchange myExchange = new MockExchange();
        myExchange.setProperty("hello", "world");

        assertNull(myExchange.getProperty("hello", Integer.class));
        assertNull(myExchange.getPropertyAsCollection("hello", Integer.class));
        assertNull(myExchange.getPropertyAsList("hello", Integer.class));
        assertNull(myExchange.getPropertyAsMap("hello", Integer.class, Integer.class));
        assertNull(myExchange.getPropertyAsSet("hello", Integer.class));
        // Optional must not be null, but empty
        assertNotNull(myExchange.getPropertyAsOptional("hello", Integer.class));
        assertFalse(myExchange.getPropertyAsOptional("hello", Integer.class).isPresent());
    }

    @Test
    public void getPropertyShouldReturnIterableProperTypes() {
        Exchange myExchange = new MockExchange();
        List<Integer> myList = List.of(1, 2);
        Set<String> mySet = Set.of("first", "second");
        Map<String, Integer> myMap = Map.of("firstKey", 1, "secondKey", 2);
        Collection<String> myCollection = List.of("a", "b");

        myExchange.setProperty("helloList", myList);
        myExchange.setProperty("helloSet", mySet);
        myExchange.setProperty("helloMap", myMap);
        myExchange.setProperty("helloCollection", myCollection);

        assertEquals(myList, myExchange.getPropertyAsList("helloList", Integer.class));
        assertEquals(mySet, myExchange.getPropertyAsSet("helloSet", String.class));
        assertEquals(myMap, myExchange.getPropertyAsMap("helloMap", String.class, Integer.class));
        assertEquals(myCollection, myExchange.getPropertyAsCollection("helloCollection", String.class));
    }

    @Test
    public void getPropertyShouldReturnNullOnWrongCastItemTypes() {
        Exchange myExchange = new MockExchange();
        List<Integer> myList = List.of(1, 2);
        Set<String> mySet = Set.of("first", "second");
        Map<String, Integer> myMap = Map.of("firstKey", 1, "secondKey", 2);
        Collection<String> myCollection = List.of("a", "b");

        myExchange.setProperty("helloList", myList);
        myExchange.setProperty("helloSet", mySet);
        myExchange.setProperty("helloMap", myMap);
        myExchange.setProperty("helloCollection", myCollection);

        assertNull(myExchange.getPropertyAsList("helloList", String.class), "Expected Integer items");
        assertNull(myExchange.getPropertyAsSet("helloSet", Integer.class), "Expected String items");
        assertNull(myExchange.getPropertyAsMap("helloMap", Integer.class, String.class), "Expected String/Integer key-value");
        assertNull(myExchange.getPropertyAsMap("helloMap", String.class, String.class), "Expected String/Integer key-value");
        assertNull(myExchange.getPropertyAsCollection("helloCollection", Integer.class), "Expected String items");
    }

    @Test
    public void getVariableShouldReturnNullOnMissingProperty() {
        Exchange myExchange = new MockExchange();
        myExchange.setVariable("hello", "world");

        assertNull(myExchange.getVariable("missing", String.class));
        assertNull(myExchange.getVariableAsCollection("missing", String.class));
        assertNull(myExchange.getVariableAsList("missing", String.class));
        assertNull(myExchange.getVariableAsMap("missing", String.class, String.class));
        assertNull(myExchange.getVariableAsSet("missing", String.class));
        // Optional must not be null, but empty
        assertNotNull(myExchange.getVariableAsOptional("missing", String.class));
        assertFalse(myExchange.getVariableAsOptional("missing", String.class).isPresent());
    }

    @Test
    public void getVariableShouldReturnNullOnClassCastException() {
        Exchange myExchange = new MockExchange();
        myExchange.setVariable("hello", "world");

        assertNull(myExchange.getVariable("hello", Integer.class));
        assertNull(myExchange.getVariableAsCollection("hello", Integer.class));
        assertNull(myExchange.getVariableAsList("hello", Integer.class));
        assertNull(myExchange.getVariableAsMap("hello", Integer.class, Integer.class));
        assertNull(myExchange.getVariableAsSet("hello", Integer.class));
        // Optional must not be null, but empty
        assertNotNull(myExchange.getVariableAsOptional("hello", Integer.class));
        assertFalse(myExchange.getVariableAsOptional("hello", Integer.class).isPresent());
    }

    @Test
    public void getVariableShouldReturnIterableProperTypes() {
        Exchange myExchange = new MockExchange();
        List<Integer> myList = List.of(1, 2);
        Set<String> mySet = Set.of("first", "second");
        Map<String, Integer> myMap = Map.of("firstKey", 1, "secondKey", 2);
        Collection<String> myCollection = List.of("a", "b");

        myExchange.setVariable("helloList", myList);
        myExchange.setVariable("helloSet", mySet);
        myExchange.setVariable("helloMap", myMap);
        myExchange.setVariable("helloCollection", myCollection);

        assertEquals(myList, myExchange.getVariableAsList("helloList", Integer.class));
        assertEquals(mySet, myExchange.getVariableAsSet("helloSet", String.class));
        assertEquals(myMap, myExchange.getVariableAsMap("helloMap", String.class, Integer.class));
        assertEquals(myCollection, myExchange.getVariableAsCollection("helloCollection", String.class));
    }

    @Test
    public void getVariableShouldReturnNullOnWrongCastItemTypes() {
        Exchange myExchange = new MockExchange();
        List<Integer> myList = List.of(1, 2);
        Set<String> mySet = Set.of("first", "second");
        Map<String, Integer> myMap = Map.of("firstKey", 1, "secondKey", 2);
        Collection<String> myCollection = List.of("a", "b");

        myExchange.setVariable("helloList", myList);
        myExchange.setVariable("helloSet", mySet);
        myExchange.setVariable("helloMap", myMap);
        myExchange.setVariable("helloCollection", myCollection);

        assertNull(myExchange.getVariableAsList("helloList", String.class), "Expected Integer items");
        assertNull(myExchange.getVariableAsSet("helloSet", Integer.class), "Expected String items");
        assertNull(myExchange.getVariableAsMap("helloMap", Integer.class, String.class), "Expected String/Integer key-value");
        assertNull(myExchange.getVariableAsMap("helloMap", String.class, String.class), "Expected String/Integer key-value");
        assertNull(myExchange.getVariableAsCollection("helloCollection", Integer.class), "Expected String items");
    }
}
