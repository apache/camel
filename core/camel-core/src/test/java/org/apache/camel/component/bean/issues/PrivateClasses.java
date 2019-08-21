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
package org.apache.camel.component.bean.issues;

/**
 * Holds Private classes to be bean-binded through their interface
 */
public final class PrivateClasses {
    public static final String EXPECTED_OUTPUT = "Hello Camel";
    public static final String METHOD_NAME = "sayHello";

    private PrivateClasses() {
        // Utility class; can't be instantiated
    }

    /**
     * Public interface through which we can bean-bind a private impl
     */
    public interface HelloCamel {
        String sayHello(String input);
    }

    static final class PackagePrivateHelloCamel implements HelloCamel {
        @Override
        public String sayHello(String input) {
            return EXPECTED_OUTPUT;
        }
    }

    private static final class PrivateHelloCamel implements HelloCamel {
        @Override
        public String sayHello(String input) {
            return EXPECTED_OUTPUT;
        }
    }

    /**
     * @return package-private implementation that can only be bean-binded
     *         through its interface
     */
    public static HelloCamel newPackagePrivateHelloCamel() {
        return new PackagePrivateHelloCamel();
    }

    /**
     * @return private implementation that can only be bean-binded through its
     *         interface
     */
    public static HelloCamel newPrivateHelloCamel() {
        return new PrivateHelloCamel();
    }
}
