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
package org.apache.camel.main.app;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.util.StringHelper;

public class Greeter implements Processor {

    private Bean1 bean;

    private GreeterMessage message;

    private Integer number;

    private CamelContext camelContext;

    public void setMessage(GreeterMessage message) {
        this.message = message;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public void setBean(Bean1 bean) {
        this.bean = bean;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String msg = exchange.getIn().getBody(String.class);
        if (camelContext != null) {
            exchange.getIn().setBody(message.getMsg() + " " + StringHelper.after(msg, "I'm ")
                                     + " (" + System.identityHashCode(camelContext) + ")");
        } else {
            exchange.getIn().setBody(message.getMsg() + " " + StringHelper.after(msg, "I'm ")
                                     + " (" + number + ")");
        }
    }

}
