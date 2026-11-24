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
package org.apache.camel.tooling.maven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The known repository names that --repos takes, such as --repos=confluent.
 */
class DefaultRepositoryResolverTest {

    @Test
    void knownNamesResolveToTheirUrl() {
        DefaultRepositoryResolver resolver = new DefaultRepositoryResolver();
        resolver.build();

        assertEquals("https://packages.confluent.io/maven/", resolver.resolveRepository("confluent"));
        assertEquals("https://maven.repository.redhat.com/ga", resolver.resolveRepository("redhat"));
        assertEquals("https://repo1.maven.org/maven2", resolver.resolveRepository("central"));
    }

    @Test
    void otherValuesAreKept() {
        DefaultRepositoryResolver resolver = new DefaultRepositoryResolver();
        resolver.build();

        assertEquals("https://repo.example.com/maven2", resolver.resolveRepository("https://repo.example.com/maven2"));
        assertEquals("my=https://repo.example.com/maven2", resolver.resolveRepository("my=https://repo.example.com/maven2"));
        assertNull(resolver.resolveRepository(" "));
    }
}
