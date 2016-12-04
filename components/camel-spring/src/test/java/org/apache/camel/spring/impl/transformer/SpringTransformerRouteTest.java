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
package org.apache.camel.spring.impl.transformer;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.transformer.TransformerRouteTest;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;

/**
 * A TransformerTest demonstrates contract based declarative transformation via Spring DSL.
 */
public class SpringTransformerRouteTest extends TransformerRouteTest {

    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this, "org/apache/camel/spring/impl/transformer/SpringTransformerRouteTest.xml");
    }

    public static class MyXmlProcessor implements Processor {
        public void process(Exchange exchange) {
            Object input = exchange.getIn().getBody();
            if (input instanceof XOrderResponse) {
                LOG.info("Endpoint: XOrderResponse -> XML");
                exchange.getIn().setBody("<XOrderResponse/>");
            } else {
                assertEquals("<XOrder/>", input);
                LOG.info("Endpoint: XML -> XOrder");
                exchange.getIn().setBody(new XOrder());
            }
        }
    }
}
