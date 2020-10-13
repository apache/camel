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
package org.apache.camel.component.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This is the test API.
 */
public class TestProxy {

    /**
     * Just saying hi
     */
    public String sayHi() {
        return "Hello!";
    }

    /**
     * Just saying hi
     *
     * @param hello should we say hello or hi
     */
    public String sayHi(boolean hello) {
        return hello ? "Hello!" : "Hi!";
    }

    /**
     * Just saying hi
     *
     * @param name your name
     */
    public String sayHi(final String name) {
        return "Hello " + name;
    }

    /**
     * Greeting method for me
     *
     * @param name your name
     */
    public final String greetMe(final String name) {
        return "Greetings " + name;
    }

    /**
     * Greeting method for us
     *
     * @param name1 your name
     * @param name2 my name
     */
    public final String greetUs(final String name1, String name2) {
        return "Greetings " + name1 + ", " + name2;
    }

    /**
     * Greeting method for all
     *
     * @param names the names to greet
     */
    public final String greetAll(List<String> names) {
        StringBuilder builder = new StringBuilder("Greetings ");
        for (String name : names) {
            builder.append(name).append(", ");
        }
        builder.delete(builder.length() - 2, builder.length());
        return builder.toString();
    }

    public final String greetWildcard(String... wildcardNames) {
        StringBuilder builder = new StringBuilder("Greetings ");
        for (String name : wildcardNames) {
            builder.append(name).append(", ");
        }
        builder.delete(builder.length() - 2, builder.length());
        return builder.toString();
    }

    public final <K, V> String greetAll(Map<K, V> peopleMap) {
        return null;
    }

    public final String[] greetTimes(String name, int times) {
        final List<String> result = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            result.add("Greetings " + name);
        }
        return result.toArray(new String[result.size()]);
    }

    public final <K extends OuterChild> String damnGenerics(K someStuff) {
        return null;
    }

    public final String greetInnerChild(InnerChild child) {
        return sayHi(child.getName());
    }

    public static class InnerChild {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
