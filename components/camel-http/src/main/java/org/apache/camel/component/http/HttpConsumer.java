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
package org.apache.camel.component.http;

import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;

/**
 * @version $Revision$
 */
public class HttpConsumer extends DefaultConsumer<HttpExchange> {

    private final HttpEndpoint endpoint;

    public HttpConsumer(HttpEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    public HttpEndpoint getEndpoint() {
        return (HttpEndpoint)super.getEndpoint();
    }

    public HttpBinding getBinding() {
        return endpoint.getBinding();
    }

    public String getPath() {
        return endpoint.getPath();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        endpoint.connect(this);
    }

    @Override
    protected void doStop() throws Exception {
        endpoint.disconnect(this);
        super.doStop();
    }

}
