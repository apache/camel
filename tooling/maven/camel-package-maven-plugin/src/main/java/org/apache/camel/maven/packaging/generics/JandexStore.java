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

package org.apache.camel.maven.packaging.generics;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;

public final class JandexStore {
    public static class Jandex {
        private final Index index;
        private final Exception exception;

        public Jandex(Index index) {
            this(index, null);
        }

        public Jandex(Exception exception) {
            this(null, exception);
        }

        private Jandex(Index index, Exception exception) {
            this.index = index;
            this.exception = exception;
        }

        public Index getIndex() {
            return index;
        }

        public Exception getException() {
            return exception;
        }

        public boolean doesNotExist() {
            if (exception instanceof NoSuchFileException) {
                return true;
            }

            return false;
        }
    }

    public static final String DEFAULT_NAME = "META-INF/jandex.idx";
    private static final Map<Path, Jandex> JANDEX_CACHE = new ConcurrentHashMap<>();

    private JandexStore() {
    }

    public static Jandex nonCachedRead(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            IndexReader reader = new IndexReader(is);
            Index index = reader.read();

            return new Jandex(index);
        } catch (Exception e) {
            return new Jandex(e);
        }
    }

    public static Jandex read(Path outputDir, String filename) {
        Path path = outputDir.resolve(filename);

        return JANDEX_CACHE.computeIfAbsent(path, k -> nonCachedRead(path));
    }

    public static Jandex read(Path outputDir) {
        Path path = outputDir.resolve(DEFAULT_NAME);

        return JANDEX_CACHE.computeIfAbsent(path, k -> nonCachedRead(path));
    }

}
