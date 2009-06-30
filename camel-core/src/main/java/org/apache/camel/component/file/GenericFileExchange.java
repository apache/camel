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
package org.apache.camel.component.file;

import java.io.IOException;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.impl.DefaultExchange;

public class GenericFileExchange<T> extends DefaultExchange {

    public GenericFileExchange(Endpoint fromEndpoint) {
        super(fromEndpoint);
    }

    public GenericFileExchange(DefaultExchange parent, GenericFile<T> file) {
        super(parent);
        setGenericFile(file);
    }

    public GenericFile<T> getGenericFile() {
        return (GenericFile<T>) getProperty(FileComponent.FILE_EXCHANGE_FILE);
    }

    public void setGenericFile(GenericFile<T> file) {
        setProperty(FileComponent.FILE_EXCHANGE_FILE, file);
        GenericFileMessage<T> in = new GenericFileMessage<T>(file);
        setIn(in);
        if (file != null) {
            file.populateHeaders(in);
        }
    }

    public Exchange newInstance() {
        return new GenericFileExchange<T>(this, getGenericFile());
    }
}
