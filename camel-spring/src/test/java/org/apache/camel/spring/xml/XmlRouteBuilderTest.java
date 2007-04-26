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
package org.apache.camel.spring.xml;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteBuilderTest;
import org.apache.camel.processor.DelegateProcessor;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * TODO: re-implement the route building logic using spring and 
 * then test it by overriding the buildXXX methods in the RouteBuilderTest
 * 
 * @version $Revision: 520164 $
 */
public class XmlRouteBuilderTest extends RouteBuilderTest {
	private ClassPathXmlApplicationContext ctx;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		ctx = new ClassPathXmlApplicationContext("org/apache/camel/spring/builder/spring_route_builder_test.xml");
	}

	@Override
	protected void tearDown() throws Exception {
		ctx.close();
		super.tearDown();
	}

	@Override
	@SuppressWarnings("unchecked")
	protected RouteBuilder buildSimpleRoute() {
		RouteBuilder builder = (RouteBuilder) ctx.getBean("buildSimpleRoute");
		assertNotNull(builder);
		return builder;
	}
	
	@Override
	protected RouteBuilder buildCustomProcessor() {
		myProcessor = (Processor<Exchange>) ctx.getBean("myProcessor");
		RouteBuilder builder = (RouteBuilder) ctx.getBean("buildCustomProcessor");
		assertNotNull(builder);
		return builder;
	}
	
	@Override
	protected RouteBuilder buildCustomProcessorWithFilter() {
		myProcessor = (Processor<Exchange>) ctx.getBean("myProcessor");
		RouteBuilder builder = (RouteBuilder) ctx.getBean("buildCustomProcessorWithFilter");
		assertNotNull(builder);
		return builder;
	}
	
	@Override
	protected RouteBuilder buildRouteWithInterceptor() {
		interceptor1 = (DelegateProcessor<Exchange>) ctx.getBean("interceptor1");
		interceptor2 = (DelegateProcessor<Exchange>) ctx.getBean("interceptor2");
		RouteBuilder builder = (RouteBuilder) ctx.getBean("buildRouteWithInterceptor");
		assertNotNull(builder);
		return builder;
	}
	
	@Override
	protected RouteBuilder buildSimpleRouteWithHeaderPredicate() {
		RouteBuilder builder = (RouteBuilder) ctx.getBean("buildSimpleRouteWithHeaderPredicate");
		assertNotNull(builder);
		return builder;
	}

	@Override
	protected RouteBuilder buildSimpleRouteWithChoice() {
		RouteBuilder builder = (RouteBuilder) ctx.getBean("buildSimpleRouteWithChoice");
		assertNotNull(builder);
		return builder;
	}
	
	
	@Override
	protected RouteBuilder buildWireTap() {
		RouteBuilder builder = (RouteBuilder) ctx.getBean("buildWireTap");
		assertNotNull(builder);
		return builder;
	}
	
	@Override
	protected RouteBuilder buildDynamicRecipientList() {
		RouteBuilder builder = (RouteBuilder) ctx.getBean("buildDynamicRecipientList");
		assertNotNull(builder);
		return builder;
	}
	
	@Override
	protected RouteBuilder buildStaticRecipientList() {
		RouteBuilder builder = (RouteBuilder) ctx.getBean("buildStaticRecipientList");
		assertNotNull(builder);
		return builder;
	}
	
	@Override
	protected RouteBuilder buildSplitter() {
		RouteBuilder builder = (RouteBuilder) ctx.getBean("buildSplitter");
		assertNotNull(builder);
		return builder;
	}

    @Override
    protected RouteBuilder buildIdempotentConsumer() {
        RouteBuilder builder = (RouteBuilder) ctx.getBean("buildIdempotentConsumer");
        assertNotNull(builder);
        return builder;
    }

    @Override
    public void testIdempotentConsumer() throws Exception {
        // TODO
    }
}
