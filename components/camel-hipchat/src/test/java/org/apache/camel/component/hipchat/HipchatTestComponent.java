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
package org.apache.camel.component.hipchat;

import org.apache.camel.CamelContext;
import org.apache.camel.component.hipchat.HipchatComponentProducerTest.PostCallback;
import org.apache.camel.spi.annotations.Component;
import org.apache.http.client.methods.CloseableHttpResponse;

@Component("hipchat")
public class HipchatTestComponent extends HipchatComponent {

    PostCallback callback;
    CloseableHttpResponse closeableHttpResponse;

    public HipchatTestComponent(CamelContext context) {
        super(context);
    }

    public HipchatTestComponent(CamelContext context, CloseableHttpResponse closeableHttpResponse) {
        super(context);
        this.closeableHttpResponse = closeableHttpResponse;
    }

    public HipchatTestComponent(CamelContext context, PostCallback callback) {
        super(context);
        this.callback = callback;
    }

    @Override
    protected HipchatEndpoint getHipchatEndpoint(String uri) {
        return new HipchatEPSuccessTestSupport(uri, this, callback, closeableHttpResponse);
    }
}
