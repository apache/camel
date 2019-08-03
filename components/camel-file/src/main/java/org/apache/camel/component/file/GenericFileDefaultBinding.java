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
package org.apache.camel.component.file;

import java.io.IOException;

import org.apache.camel.Exchange;

/**
 * Default binding for generic file.
 */
public class GenericFileDefaultBinding<T> implements GenericFileBinding<T> {
    private Object body;

    @Override
    public Object getBody(GenericFile<T> file) {
        return body;
    }

    @Override
    public void setBody(GenericFile<T> file, Object body) {
        this.body = body;
    }

    @Override
    public void loadContent(Exchange exchange, GenericFile<?> file) throws IOException {
        // noop as the body is already loaded
    }
}
