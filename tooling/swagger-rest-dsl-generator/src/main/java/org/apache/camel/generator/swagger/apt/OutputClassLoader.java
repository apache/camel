/**
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
package org.apache.camel.generator.swagger.apt;

import java.io.IOException;
import java.io.InputStream;

import javax.tools.FileObject;

/**
 * A {@link ClassLoader} that loads the specified {@link FileObject} generated
 * during compilation.
 */
final class OutputClassLoader extends ClassLoader {
    OutputClassLoader() {
        super(OutputClassLoader.class.getClassLoader());
    }

    <T> Class<T> load(final FileObject file) throws IOException {
        try (InputStream classStream = file.openInputStream()) {
            final byte[] buff = new byte[classStream.available()];

            classStream.read(buff);

            @SuppressWarnings("unchecked")
            final Class<T> theClass = (Class<T>) defineClass(null, buff, 0, buff.length);

            return theClass;
        }
    }
}