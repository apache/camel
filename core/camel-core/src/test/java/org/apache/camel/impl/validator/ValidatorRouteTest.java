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
package org.apache.camel.impl.validator;

import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Validator;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A ValidatorRouteTest demonstrates contract based declarative validation via
 * Java DSL.
 */
public class ValidatorRouteTest extends ContextTestSupport {

    protected static final Logger LOG = LoggerFactory.getLogger(ValidatorRouteTest.class);
    private static final String VALIDATOR_INVOKED = "validator-invoked";

    @Test
    public void testPredicateValidator() throws Exception {
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getIn().setBody("{name:XOrder}");
        Exchange answerEx = template.send("direct:predicate", exchange);
        if (answerEx.getException() != null) {
            throw answerEx.getException();
        }
        assertEquals("{name:XOrderResponse}", answerEx.getIn().getBody(String.class));
    }

    @Test
    public void testEndpointValidator() throws Exception {
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getIn().setBody("<XOrder/>");
        Exchange answerEx = template.send("direct:endpoint", exchange);
        if (answerEx.getException() != null) {
            throw answerEx.getException();
        }
        assertEquals("<XOrderResponse/>", answerEx.getMessage().getBody(String.class));
        assertEquals(MyXmlEndpoint.class, answerEx.getProperty(VALIDATOR_INVOKED));
    }

    @Test
    public void testCustomValidator() throws Exception {
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getIn().setBody("name=XOrder");
        Exchange answerEx = template.send("direct:custom", exchange);
        if (answerEx.getException() != null) {
            throw answerEx.getException();
        }
        assertEquals("name=XOrderResponse", answerEx.getMessage().getBody(String.class));
        assertEquals(OtherXOrderResponseValidator.class, answerEx.getProperty(VALIDATOR_INVOKED));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                validator().type("json").withExpression(bodyAs(String.class).contains("{name:XOrder}"));
                from("direct:predicate").inputTypeWithValidate("json:JsonXOrder").outputType("json:JsonXOrderResponse").setBody(simple("{name:XOrderResponse}"));

                context.addComponent("myxml", new MyXmlComponent());
                validator().type("xml:XmlXOrderResponse").withUri("myxml:endpoint");
                from("direct:endpoint").inputType("xml:XmlXOrder").outputTypeWithValidate("xml:XmlXOrderResponse").validate(exchangeProperty(VALIDATOR_INVOKED).isNull())
                    .setBody(simple("<XOrderResponse/>"));

                validator().type("other:OtherXOrder").withJava(OtherXOrderValidator.class);
                validator().type("other:OtherXOrderResponse").withJava(OtherXOrderResponseValidator.class);
                from("direct:custom").inputTypeWithValidate("other:OtherXOrder").outputTypeWithValidate("other:OtherXOrderResponse")
                    .validate(exchangeProperty(VALIDATOR_INVOKED).isEqualTo(OtherXOrderValidator.class)).setBody(simple("name=XOrderResponse"));
            }
        };
    }

    public static class MyXmlComponent extends DefaultComponent {
        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            return new MyXmlEndpoint();
        }

    }

    public static class MyXmlEndpoint extends DefaultEndpoint {
        @Override
        public Producer createProducer() throws Exception {
            return new DefaultAsyncProducer(this) {
                @Override
                public boolean process(Exchange exchange, AsyncCallback callback) {
                    exchange.setProperty(VALIDATOR_INVOKED, MyXmlEndpoint.class);
                    assertEquals("<XOrderResponse/>", exchange.getIn().getBody());
                    callback.done(true);
                    return true;
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return null;
        }

        @Override
        public boolean isSingleton() {
            return false;
        }

        @Override
        protected String createEndpointUri() {
            return "myxml:endpoint";
        }
    }

    public static class OtherXOrderValidator extends Validator {
        @Override
        public void validate(Message message, DataType type) throws ValidationException {
            message.getExchange().setProperty(VALIDATOR_INVOKED, OtherXOrderValidator.class);
            assertEquals("name=XOrder", message.getBody());
            LOG.info("Java validation: other XOrder");
        }
    }

    public static class OtherXOrderResponseValidator extends Validator {
        @Override
        public void validate(Message message, DataType type) throws ValidationException {
            message.getExchange().setProperty(VALIDATOR_INVOKED, OtherXOrderResponseValidator.class);
            assertEquals("name=XOrderResponse", message.getBody());
            LOG.info("Java validation: other XOrderResponse");
        }
    }

}
