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
package org.apache.camel.component.file.azure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@SuppressWarnings("static-method")
public class FilesPathTests {

    @Test
    void splitAbsolutePreservingRootShouldReturnRootAndSteps() {
        assertArrayEquals(new String[] { "/", "1", "2" }, FilesPath.splitToSteps("/1/2", true));
    }

    @Test
    void splitAbsoluteWithoutPreservingRootShouldReturnStepsOnly() {
        assertArrayEquals(new String[] { "1", "2" }, FilesPath.splitToSteps("/1/2", false));
    }

    @Test
    void splitRelativePreservingRootShouldReturnStepsOnly() {
        assertArrayEquals(new String[] { "1", "2" }, FilesPath.splitToSteps("1/2", true));
    }

    @Test
    void splitRootPreservingRootShouldReturnRoot() {
        assertArrayEquals(new String[] { "/" }, FilesPath.splitToSteps("/", true));
    }

    @Test
    void splitWithoutSeparatorShouldReturnInput() {
        // by observation, Camel devs were uncertain what is returned ...
        assertArrayEquals(new String[] { "a path" }, FilesPath.split("a path"));
    }

}
