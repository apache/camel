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
package org.apache.camel;

import java.util.Date;

import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CamelExceptionsTest extends ContextTestSupport {

    @Test
    public void testExpectedBodyTypeException() {
        Exchange exchange = new DefaultExchange(context);

        ExpectedBodyTypeException e = new ExpectedBodyTypeException(exchange, Integer.class);
        assertSame(exchange, e.getExchange());
        assertEquals(Integer.class, e.getExpectedBodyType());
    }

    @Test
    public void testExpressionEvaluationException() {
        Expression exp = ExpressionBuilder.constantExpression("foo");
        Exchange exchange = new DefaultExchange(context);

        ExpressionEvaluationException e
                = new ExpressionEvaluationException(exp, exchange, new IllegalArgumentException("Damn"));
        assertSame(exchange, e.getExchange());
        assertSame(exp, e.getExpression());
        assertNotNull(e.getCause());
    }

    @Test
    public void testFailedToCreateConsumerException() {
        Endpoint endpoint = context.getEndpoint("seda:foo");
        FailedToCreateConsumerException e = new FailedToCreateConsumerException(endpoint, new IllegalArgumentException("Damn"));

        assertEquals(endpoint.getEndpointUri(), e.getUri());
        assertNotNull(e.getCause());
    }

    @Test
    public void testFailedToCreateProducerException() {
        Endpoint endpoint = context.getEndpoint("seda:foo");
        FailedToCreateProducerException e = new FailedToCreateProducerException(endpoint, new IllegalArgumentException("Damn"));

        assertEquals(endpoint.getEndpointUri(), e.getUri());
        assertNotNull(e.getCause());
    }

    @Test
    public void testInvalidPayloadRuntimeException() {
        Exchange exchange = new DefaultExchange(context);

        InvalidPayloadRuntimeException e = new InvalidPayloadRuntimeException(exchange, Integer.class);
        assertSame(exchange, e.getExchange());
        assertEquals(Integer.class, e.getType());

        InvalidPayloadRuntimeException e2 = new InvalidPayloadRuntimeException(exchange, Integer.class, exchange.getIn());
        assertSame(exchange, e2.getExchange());
        assertEquals(Integer.class, e2.getType());

        InvalidPayloadRuntimeException e3 = new InvalidPayloadRuntimeException(
                exchange, Integer.class, exchange.getIn(), new IllegalArgumentException("Damn"));
        assertSame(exchange, e3.getExchange());
        assertEquals(Integer.class, e3.getType());
    }

    @Test
    public void testRuntimeTransformException() {
        RuntimeTransformException e = new RuntimeTransformException("Forced");
        assertEquals("Forced", e.getMessage());
        assertNull(e.getCause());

        RuntimeTransformException e2 = new RuntimeTransformException("Forced", new IllegalAccessException("Damn"));
        assertEquals("Forced", e2.getMessage());
        assertNotNull(e2.getCause());

        RuntimeTransformException e3 = new RuntimeTransformException(new IllegalAccessException("Damn"));
        assertEquals("java.lang.IllegalAccessException: Damn", e3.getMessage());
        assertNotNull(e3.getCause());
    }

    @Test
    public void testRuntimeExpressionException() {
        RuntimeExpressionException e = new RuntimeExpressionException("Forced");
        assertEquals("Forced", e.getMessage());
        assertNull(e.getCause());

        RuntimeExpressionException e2 = new RuntimeExpressionException("Forced", new IllegalAccessException("Damn"));
        assertEquals("Forced", e2.getMessage());
        assertNotNull(e2.getCause());

        RuntimeExpressionException e3 = new RuntimeExpressionException(new IllegalAccessException("Damn"));
        assertEquals("java.lang.IllegalAccessException: Damn", e3.getMessage());
        assertNotNull(e3.getCause());
    }

    @Test
    public void testRollbackExchangeException() {
        Exchange exchange = new DefaultExchange(context);

        RollbackExchangeException e = new RollbackExchangeException(exchange, new IllegalAccessException("Damn"));
        assertNotNull(e.getMessage());
        assertSame(exchange, e.getExchange());

        RollbackExchangeException e2 = new RollbackExchangeException("Forced", exchange, new IllegalAccessException("Damn"));
        assertNotNull(e2.getMessage());
        assertSame(exchange, e2.getExchange());
    }

    @Test
    public void testValidationException() {
        Exchange exchange = new DefaultExchange(context);

        ValidationException e = new ValidationException(exchange, "Forced");
        assertNotNull(e.getMessage());
        assertSame(exchange, e.getExchange());

        ValidationException e2 = new ValidationException("Forced", exchange, new IllegalAccessException("Damn"));
        assertNotNull(e2.getMessage());
        assertSame(exchange, e2.getExchange());
    }

    @Test
    public void testNoSuchBeanException() {
        NoSuchBeanException e = new NoSuchBeanException("foo");
        assertEquals("foo", e.getName());
        assertNull(e.getCause());

        NoSuchBeanException e2 = new NoSuchBeanException("foo", new IllegalArgumentException("Damn"));
        assertEquals("foo", e2.getName());
        assertNotNull(e2.getCause());
    }

    @Test
    public void testCamelExecutionException() {
        Exchange exchange = new DefaultExchange(context);

        CamelExecutionException e = new CamelExecutionException("Forced", exchange);
        assertNotNull(e.getMessage());
        assertSame(exchange, e.getExchange());
        assertNull(e.getCause());

        CamelExecutionException e2 = new CamelExecutionException("Forced", exchange, new IllegalArgumentException("Damn"));
        assertNotNull(e2.getMessage());
        assertSame(exchange, e2.getExchange());
        assertNotNull(e2.getCause());
    }

    @Test
    public void testCamelException() {
        CamelException e = new CamelException();
        assertNull(e.getCause());

        CamelException e2 = new CamelException("Forced");
        assertNull(e2.getCause());
        assertEquals("Forced", e2.getMessage());

        CamelException e3 = new CamelException("Forced", new IllegalArgumentException("Damn"));
        assertNotNull(e3.getCause());
        assertEquals("Forced", e3.getMessage());

        CamelException e4 = new CamelException(new IllegalArgumentException("Damn"));
        assertNotNull(e4.getCause());
        assertNotNull(e4.getMessage());
    }

    @Test
    public void testServiceStatus() {
        assertTrue(ServiceStatus.Started.isStarted());
        assertFalse(ServiceStatus.Starting.isStarted());
        assertFalse(ServiceStatus.Starting.isStoppable());
        assertFalse(ServiceStatus.Stopped.isStarted());
        assertFalse(ServiceStatus.Stopping.isStarted());

        assertTrue(ServiceStatus.Stopped.isStopped());
        assertFalse(ServiceStatus.Starting.isStopped());
        assertFalse(ServiceStatus.Started.isStopped());
        assertFalse(ServiceStatus.Stopping.isStopped());

        assertTrue(ServiceStatus.Stopped.isStartable());
        assertFalse(ServiceStatus.Started.isStartable());
        assertFalse(ServiceStatus.Starting.isStartable());
        assertFalse(ServiceStatus.Stopping.isStartable());

        assertTrue(ServiceStatus.Started.isStoppable());
        assertFalse(ServiceStatus.Starting.isStoppable());
        assertFalse(ServiceStatus.Stopped.isStoppable());
        assertFalse(ServiceStatus.Stopping.isStoppable());
    }

    @Test
    public void testRuntimeExchangeException() {
        Exchange exchange = new DefaultExchange(context);

        RuntimeExchangeException e = new RuntimeExchangeException("Forced", exchange);
        assertNotNull(e.getMessage());
        assertSame(exchange, e.getExchange());

        RuntimeExchangeException e2 = new RuntimeExchangeException("Forced", null);
        assertNotNull(e2.getMessage());
        assertNull(e2.getExchange());
    }

    @Test
    public void testExchangePattern() {
        assertTrue(ExchangePattern.InOnly.isInCapable());
        assertTrue(ExchangePattern.InOut.isInCapable());

        assertFalse(ExchangePattern.InOnly.isOutCapable());
        assertTrue(ExchangePattern.InOut.isOutCapable());

        assertEquals(ExchangePattern.InOnly, ExchangePattern.asEnum("InOnly"));
        assertEquals(ExchangePattern.InOut, ExchangePattern.asEnum("InOut"));

        assertThrows(IllegalArgumentException.class, () -> ExchangePattern.asEnum("foo"),
                "Should have thrown an exception");
    }

    @Test
    public void testInvalidPayloadException() {
        Exchange exchange = new DefaultExchange(context);

        InvalidPayloadException e = new InvalidPayloadException(exchange, Integer.class);
        assertSame(exchange, e.getExchange());
        assertEquals(Integer.class, e.getType());
    }

    @Test
    public void testExchangeTimedOutException() {
        Exchange exchange = new DefaultExchange(context);

        ExchangeTimedOutException e = new ExchangeTimedOutException(exchange, 5000);
        assertSame(exchange, e.getExchange());
        assertEquals(5000, e.getTimeout());
    }

    @Test
    public void testExpressionIllegalSyntaxException() {
        ExpressionIllegalSyntaxException e = new ExpressionIllegalSyntaxException("foo");
        assertEquals("foo", e.getExpression());
    }

    @Test
    public void testNoFactoryAvailableException() {
        NoFactoryAvailableException e = new NoFactoryAvailableException("killer", new IllegalArgumentException("Damn"));
        assertNotNull(e.getCause());
        assertEquals("killer", e.getUri());
    }

    @Test
    public void testCamelExchangeException() {
        Exchange exchange = new DefaultExchange(context);

        CamelExchangeException e = new CamelExchangeException("Forced", exchange);
        assertNotNull(e.getMessage());
        assertSame(exchange, e.getExchange());
    }

    @Test
    public void testNoSuchHeaderException() {
        Exchange exchange = new DefaultExchange(context);

        NoSuchHeaderException e = new NoSuchHeaderException(exchange, "foo", Integer.class);
        assertEquals(Integer.class, e.getType());
        assertEquals("foo", e.getHeaderName());
        assertSame(exchange, e.getExchange());
    }

    @Test
    public void testNoSuchPropertyException() {
        Exchange exchange = new DefaultExchange(context);

        NoSuchPropertyException e = new NoSuchPropertyException(exchange, "foo", Integer.class);
        assertEquals(Integer.class, e.getType());
        assertEquals("foo", e.getPropertyName());
        assertSame(exchange, e.getExchange());
    }

    @Test
    public void testRuntimeCamelException() {
        RuntimeCamelException e = new RuntimeCamelException();
        assertNull(e.getMessage());
        assertNull(e.getCause());
    }

    @Test
    public void testFailedToStartRouteException() {
        FailedToStartRouteException e
                = new FailedToStartRouteException("myRoute", "Forced error", new IllegalArgumentException("Forced"));
        assertNotNull(e.getMessage());
        assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
    }

    @Test
    public void testNoTypeConversionAvailableException() {
        NoTypeConversionAvailableException e = new NoTypeConversionAvailableException("foo", Date.class);
        assertEquals("foo", e.getValue());
        assertEquals(Date.class, e.getToType());
        assertEquals(String.class, e.getFromType());

        NoTypeConversionAvailableException e2 = new NoTypeConversionAvailableException(null, Date.class);
        assertNull(e2.getValue());
        assertEquals(Date.class, e2.getToType());
        assertNull(e2.getFromType());
    }

    @Test
    public void testResolveEndpointFailedException() {
        ResolveEndpointFailedException e = new ResolveEndpointFailedException("foo:bar");
        assertEquals("foo:bar", e.getUri());
    }

}
