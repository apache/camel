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
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version
 */
public class XQueryWithExtensionTest extends CamelTestSupport {

    private Configuration conf;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        conf = new Configuration();
        conf.registerExtensionFunction(new SimpleExtension());

        jndi.bind("saxonConf", conf);
        return jndi;
    }

    @Test
    public void testWithExtension() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("<transformed extension-function-render=\"arg1[test]\"/>");

        template.sendBody("direct:start", "<body>test</body>");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("xquery:org/apache/camel/component/xquery/transformWithExtension.xquery?configuration=#saxonConf")
                    .to("mock:result");
            }
        };
    }

    /**
     * This is a very simple example of a saxon extension function. We will use
     * this for testing purposes.
     * <p/>
     * Example: <code>efx:simple('some text')</code> will be rendered to
     * <code>arg1[some text]</code> and returned in the XQuery response.
     */
    public static final class SimpleExtension extends ExtensionFunctionDefinition {

        private static final long serialVersionUID = 1L;

        @Override
        public SequenceType[] getArgumentTypes() {
            return new SequenceType[]{SequenceType.SINGLE_STRING};
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

                @Override
                public Sequence call(XPathContext xPathContext, Sequence[] sequences) throws XPathException {
                    // get value of first arg passed to the function
                    Item arg1 = sequences[0].head();
                    String arg1Val = arg1.getStringValue();

                    // return a altered version of the first arg
                    return new StringValue("arg1[" + arg1Val + "]");
                }
            };
        }
    }

}