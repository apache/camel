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
package org.apache.camel.component.google.drive;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.component.google.drive.internal.GoogleDriveApiCollection;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.component.ApiMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The methodName option lists the methods of the Drive v3 APIs, such as create and export, and not the Drive v2
 * methods, such as insert, which the component no longer has.
 */
class GoogleDriveApiMethodsTest {

    @Test
    void shouldListTheMethodNamesOfTheApisInTheMethodNameOption() throws Exception {
        Set<String> methodNames = new TreeSet<>();
        for (Class<? extends ApiMethod> api : GoogleDriveApiCollection.getCollection().getApiMethods().keySet()) {
            for (ApiMethod method : api.getEnumConstants()) {
                methodNames.add(method.getName());
            }
        }
        String enums = GoogleDriveConfiguration.class.getDeclaredField("methodName").getAnnotation(UriPath.class).enums();

        assertEquals(methodNames, new TreeSet<>(Arrays.asList(enums.split(","))));
    }
}
