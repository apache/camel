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
package org.apache.camel.component.jms.bind;

import java.util.Map;

import org.apache.camel.Consume;
import org.apache.camel.EndpointInject;
import org.apache.camel.Headers;
import org.apache.camel.ProducerTemplate;

/**
 * @version 
 */
public class MyBean {
    private Map<?, ?> headers;
    private String body;
    @EndpointInject(uri = "mock:result")
    private ProducerTemplate producer;

    @Consume(uri = "activemq:Test.BindingQueue")
    public void myMethod(@Headers Map<?, ?> headers, String body) {
        this.headers = headers;
        this.body = body;

        // now lets notify we've completed
        producer.sendBody("Completed");
    }

    public String getBody() {
        return body;
    }

    public Map<?, ?> getHeaders() {
        return headers;
    }
}
