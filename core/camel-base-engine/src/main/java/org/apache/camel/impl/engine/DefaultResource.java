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
package org.apache.camel.impl.engine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.apache.camel.spi.Resource;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.function.ThrowingSupplier;

public class DefaultResource implements Resource {
    private final String location;
    private final ThrowingSupplier<InputStream, IOException> inputStreamSupplier;

    public DefaultResource(String location, ThrowingSupplier<InputStream, IOException> inputStreamSupplier) {
        this.location = ObjectHelper.notNull(location, "location");
        this.inputStreamSupplier = ObjectHelper.notNull(inputStreamSupplier, "inputStreamSupplier");
    }

    @Override
    public String getLocation() {
        return location;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return inputStreamSupplier.get();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Resource)) {
            return false;
        }
        Resource resource = (Resource) o;
        return getLocation().equals(resource.getLocation());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLocation());
    }

    @Override
    public String toString() {
        return "DefaultResource{" +
               "location='" + location + '\'' +
               '}';
    }
}
