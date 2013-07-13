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
package org.apache.camel.component.xquery;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version
 */
public class XQueryWithExtensionTest extends CamelSpringTestSupport {

    @Test
    public void testWithExtension() throws Exception {

	// have to get to the XQueryBuilder somehow to register our custom saxon
	// extension function
	ProcessorEndpoint pep = (ProcessorEndpoint) this.context
		.getEndpoint("xquery://org/apache/camel/component/xquery/transformWithExtension.xquery");

	XQueryBuilder xqb = (XQueryBuilder) pep.getProcessor();

	// add a custom configuration to the XQueryBuilder with an externally
	// registered xpath extension
	Configuration conf = new Configuration();
	conf.registerExtensionFunction(new SimpleExtention());
	xqb.setConfiguration(conf);

	MockEndpoint mock = getMockEndpoint("mock:result");
	mock.expectedBodiesReceived("<transformed extension-function-render=\"arg1[test]\"/>");

	template.sendBody("direct:start", "<body>test</body>");

	assertMockEndpointsSatisfied();
    }

    protected ClassPathXmlApplicationContext createApplicationContext() {
	return new ClassPathXmlApplicationContext(
		"org/apache/camel/component/xquery/xqueryWithExtensionTest.xml");
    }

    /**
     * This is a very simple example of a saxon extension function. We will use
     * this for testing purposes.
     * 
     * Example: <code>efx:simple('some text')</code> will be rendered to
     * <code>arg1[some text]</code> and returned in the XQuery response.
     * 
     */
    public static class SimpleExtention extends ExtensionFunctionDefinition {

	private static final long serialVersionUID = 1L;

	@Override
	public SequenceType[] getArgumentTypes() {
	    return new SequenceType[] { SequenceType.SINGLE_STRING };
	}

	@Override
	public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
	    return SequenceType.SINGLE_STRING;
	}

	@Override
	public StructuredQName getFunctionQName() {
	    return new StructuredQName("efx", "http://test/saxon/ext", "simple");
	}

	@Override
	public ExtensionFunctionCall makeCallExpression() {
	    return new ExtensionFunctionCall() {

		private static final long serialVersionUID = 1L;

		@SuppressWarnings("rawtypes")
		@Override
		public SequenceIterator<? extends Item> call(
			SequenceIterator<? extends Item>[] args,
			XPathContext context) throws XPathException {

		    // get value of first arg passed to the function
		    Item<?> arg1 = args[0].next();
		    String arg1Val = arg1.getStringValue();

		    // return a altered version of the first arg
		    StringValue sv = new StringValue("arg1[" + arg1Val + "]");
		    return SingletonIterator.makeIterator(sv);
		}

	    };
	}
    }

}