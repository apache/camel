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
package org.apache.camel.component.beanstalk.processors;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.beanstalk.BeanstalkEndpoint;
import org.apache.camel.util.ExchangeHelper;

abstract class DefaultCommand implements Command {
    protected final BeanstalkEndpoint endpoint;

    DefaultCommand(BeanstalkEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    protected Message getAnswerMessage(final Exchange exchange) {
        Message answer = exchange.getIn();
        if (ExchangeHelper.isOutCapable(exchange)) {
            answer = exchange.getOut();
            // preserve headers
            answer.getHeaders().putAll(exchange.getIn().getHeaders());
        }
        return answer;
    }

    protected void answerWith(final Exchange exchange, final String header, final Object value) {
        final Message answer = getAnswerMessage(exchange);
        answer.setHeader(header, value);
    }
}