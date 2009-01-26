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
package org.apache.camel.component.file.remote;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileExchange;
import org.apache.camel.impl.DefaultExchange;

public class RemoteFileExchange<T> extends GenericFileExchange<T> {

    public RemoteFileExchange(CamelContext context, ExchangePattern pattern) {
        super(context, pattern);
    }

    public RemoteFileExchange(CamelContext context) {
        super(context);
    }

    public RemoteFileExchange(DefaultExchange parent, RemoteFile<T> remoteFile) {
        super(parent, remoteFile);
    }

    public RemoteFileExchange(Endpoint fromEndpoint, ExchangePattern pattern) {
        super(fromEndpoint, pattern);
    }

    public RemoteFileExchange(Endpoint fromEndpoint) {
        super(fromEndpoint);
    }

    public RemoteFileExchange(Exchange parent) {
        super(parent);
    }

    public RemoteFileExchange(RemoteFileEndpoint<T> endpoint, ExchangePattern pattern, RemoteFile<T> genericFile) {
        super(endpoint, pattern, genericFile);
    }

    @Override
    protected void populateHeaders(GenericFile<T> remoteFile) {
        super.populateHeaders(remoteFile);
        if (remoteFile != null) {
            getIn().setHeader("file.remote.host", ((RemoteFile<T>) remoteFile).getHostname());
        }
    }

}
