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
package org.apache.camel.processor;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.saga.InMemorySagaService;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.saga.CamelSagaService;
import org.junit.Assert;
import org.junit.Test;

public class SagaPropagationTest extends ContextTestSupport {

    private List<String> sagaIds;

    @Test
    public void testPropagationRequired() throws Exception {
        context.createFluentProducerTemplate().to("direct:required").request();

        assertListSize(sagaIds, 3);
        assertUniqueNonNullSagaIds(1);
    }

    @Test
    public void testPropagationRequiresNew() throws Exception {
        context.createFluentProducerTemplate().to("direct:requiresNew").request();

        assertListSize(sagaIds, 3);
        assertUniqueNonNullSagaIds(3);
    }

    @Test
    public void testPropagationNotSupported() throws Exception {
        context.createFluentProducerTemplate().to("direct:notSupported").request();

        assertListSize(sagaIds, 4);
        assertNonNullSagaIds(1);
    }

    @Test
    public void testPropagationSupports() throws Exception {
        context.createFluentProducerTemplate().to("direct:supports").request();

        assertListSize(sagaIds, 2);
        assertNonNullSagaIds(2);
    }

    @Test
    public void testPropagationMandatory() throws Exception {
        try {
            context.createFluentProducerTemplate().to("direct:mandatory").request();
            Assert.fail("Exception not thrown");
        } catch (CamelExecutionException ex) {
            // fine
        }
    }

    @Test
    public void testPropagationNever() throws Exception {
        try {
            context.createFluentProducerTemplate().to("direct:never").request();
            Assert.fail("Exception not thrown");
        } catch (CamelExecutionException ex) {
            // fine
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        this.sagaIds = new LinkedList<>();

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                CamelSagaService sagaService = new InMemorySagaService();
                context.addService(sagaService);

                // REQUIRED

                from("direct:required").saga().process(addSagaIdToList()).to("direct:required2");

                from("direct:required2").saga().propagation(SagaPropagation.REQUIRED).process(addSagaIdToList()).to("direct:required3");

                from("direct:required3").saga().process(addSagaIdToList());

                // REQUIRES_NEW

                from("direct:requiresNew").saga().propagation(SagaPropagation.REQUIRES_NEW).process(addSagaIdToList()).to("direct:requiresNew2").to("direct:requiresNew2");

                from("direct:requiresNew2").saga().propagation(SagaPropagation.REQUIRES_NEW).process(addSagaIdToList());

                // NOT_SUPPORTED

                from("direct:notSupported").process(addSagaIdToList()).to("direct:notSupported2").to("direct:notSupported3");

                from("direct:notSupported2").saga() // required
                    .process(addSagaIdToList()).to("direct:notSupported3");

                from("direct:notSupported3").saga().propagation(SagaPropagation.NOT_SUPPORTED).process(addSagaIdToList());

                // SUPPORTS

                from("direct:supports").to("direct:supports2").to("direct:supports3");

                from("direct:supports2").saga() // required
                    .to("direct:supports3");

                from("direct:supports3").saga().propagation(SagaPropagation.SUPPORTS).process(addSagaIdToList());

                // MANDATORY

                from("direct:mandatory").to("direct:mandatory2");

                from("direct:mandatory2").saga().propagation(SagaPropagation.MANDATORY).process(addSagaIdToList());

                // NEVER

                from("direct:never").saga().to("direct:never2");

                from("direct:never2").saga().propagation(SagaPropagation.NEVER).process(addSagaIdToList());

            }
        };
    }

    private Processor addSagaIdToList() {
        return ex -> sagaIds.add(ex.getMessage().getHeader(Exchange.SAGA_LONG_RUNNING_ACTION, String.class));
    }

    private void assertUniqueNonNullSagaIds(int num) {
        Set<String> uniqueNonNull = this.sagaIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (uniqueNonNull.size() != num) {
            Assert.fail("Expeced size " + num + ", actual " + uniqueNonNull.size());
        }
    }

    private void assertNonNullSagaIds(int num) {
        List<String> nonNull = this.sagaIds.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (nonNull.size() != num) {
            Assert.fail("Expeced size " + num + ", actual " + nonNull.size());
        }
    }

}
