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
package sample.camel;

import org.apache.camel.Consume;
import org.apache.camel.Produce;
import org.springframework.stereotype.Component;

/**
 * A POJO that listen to messages from the seda:numbers endpoint via {@link Consume}
 * and then via {@link MagicNumber} and {@link Produce} sends a message that will
 * be printed in the console.
 */
@Component
public class NumberPojo {

    // sends the message to the stream:out endpoint but hidden behind this interface
    // so the client java code below can use the interface method instead of Camel's
    // FluentProducerTemplate or ProducerTemplate APIs
    @Produce(uri = "stream:out")
    private MagicNumber magic;

    // only consume when the predicate matches, eg when the message body is lower than 100
    @Consume(uri = "direct:numbers", predicate = "${body} < 100")
    public void lowNumber(int number) {
        magic.onMagicNumber("Got a low number " + number);
    }

    // only consume when the predicate matches, eg when the message body is higher or equal to 100
    @Consume(uri = "direct:numbers", predicate = "${body} >= 100")
    public void highNumber(int number) {
        magic.onMagicNumber("Got a high number " + number);
    }

}
