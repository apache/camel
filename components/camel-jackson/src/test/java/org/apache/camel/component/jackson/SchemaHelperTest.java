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
package org.apache.camel.component.jackson;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaHelperTest {

    @Test
    void streamCacheIsNoPojo() {
        // CAMEL-27258: the binary data type keeps a stream cache body, which holds raw content and is no POJO
        assertFalse(SchemaHelper.isPojo(InputStreamCache.class));
        assertFalse(SchemaHelper.isPojo(byte[].class));
        assertFalse(SchemaHelper.isPojo(String.class));
        assertTrue(SchemaHelper.isPojo(TestPojo.class));
    }

    @Test
    void streamCacheBodyHasNoContentClass() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getMessage().setBody(new InputStreamCache("{ \"name\": \"Goofy\" }".getBytes(StandardCharsets.UTF_8)));

        assertNull(SchemaHelper.resolveContentClass(exchange, null));
        assertEquals("fallback", SchemaHelper.resolveContentClass(exchange, "fallback"));

        exchange.getMessage().setBody(new TestPojo());
        assertEquals(TestPojo.class.getName(), SchemaHelper.resolveContentClass(exchange, null));
    }

    @Test
    void fileIsNoPojo() {
        // CAMEL-27302: the binary data type keeps a file body (CAMEL-27267), which is a handle to raw content and is no POJO
        assertFalse(SchemaHelper.isPojo(TestWrappedFile.class));
        assertFalse(SchemaHelper.isPojo(File.class));
        assertFalse(SchemaHelper.isPojo(Path.class));
    }

    @Test
    void fileBodyHasNoContentClass() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getMessage().setBody(new TestWrappedFile(new File("target/person.json")));

        assertNull(SchemaHelper.resolveContentClass(exchange, null));
        assertEquals("fallback", SchemaHelper.resolveContentClass(exchange, "fallback"));
    }

    private static final class TestWrappedFile implements WrappedFile<File> {

        private final File file;

        TestWrappedFile(File file) {
            this.file = file;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public Object getBody() {
            return file;
        }

        @Override
        public long getFileLength() {
            return file.length();
        }
    }
}
