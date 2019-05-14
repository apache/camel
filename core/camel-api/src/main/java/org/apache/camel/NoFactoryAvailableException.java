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
package org.apache.camel;

import java.io.IOException;

/**
 * Thrown if no factory resource is available for the given URI
 */
public class NoFactoryAvailableException extends IOException {

    private final String uri;

    public NoFactoryAvailableException(String uri) {
        super("Cannot find factory class for resource: " + uri);
        this.uri = uri;
    }

    public NoFactoryAvailableException(String uri, Throwable cause) {
        this(uri);
        initCause(cause);
    }

    public String getUri() {
        return uri;
    }
}
