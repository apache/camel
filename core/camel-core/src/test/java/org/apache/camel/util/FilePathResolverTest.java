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
package org.apache.camel.util;

import org.junit.Assert;
import org.junit.Test;

public class FilePathResolverTest extends Assert {

    @Test
    public void testFilePathResolver() throws Exception {
        assertEquals("/foo/bar", FilePathResolver.resolvePath("/foo/bar"));

        String tmp = System.getProperty("java.io.tmpdir");
        assertEquals(tmp + "foo", FilePathResolver.resolvePath("${java.io.tmpdir}foo"));

        System.setProperty("beer", "Carlsberg");
        assertEquals(tmp + "foo/Carlsberg", FilePathResolver.resolvePath("${java.io.tmpdir}foo/${beer}"));
    }
}
