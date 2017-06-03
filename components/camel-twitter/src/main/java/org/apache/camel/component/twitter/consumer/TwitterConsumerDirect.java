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
package org.apache.camel.component.twitter.consumer;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.impl.DefaultConsumer;

/**
 * Camel DirectConsumer implementation.
 */
@Deprecated
public class TwitterConsumerDirect extends DefaultConsumer {

    private final AbstractTwitterConsumerHandler twitter4jConsumer;

    public TwitterConsumerDirect(TwitterEndpoint endpoint, Processor processor, AbstractTwitterConsumerHandler twitter4jConsumer) {
        super(endpoint, processor);

        this.twitter4jConsumer = twitter4jConsumer;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        List<Exchange> exchanges = twitter4jConsumer.directConsume();
        for (int i = 0; i < exchanges.size(); i++) {
            getProcessor().process(exchanges.get(i));
        }
    }
}
