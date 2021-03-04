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
package org.apache.camel.urlhandler.custom;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.spi.Resource;
import org.apache.camel.support.ResourceResolverSupport;

public class Handler extends ResourceResolverSupport {
    public Handler() {
        super("custom");
    }

    @Override
    protected Resource createResource(String location) {
        return new Resource() {
            @Override
            public String getLocation() {
                return location;
            }

            @Override
            public boolean exists() {
                return true;
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(getRemaining(location).getBytes(StandardCharsets.UTF_8));
            }
        };
    }
}
