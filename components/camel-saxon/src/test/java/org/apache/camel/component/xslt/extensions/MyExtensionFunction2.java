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
package org.apache.camel.component.xslt.extensions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyExtensionFunction2 extends ExtensionFunctionDefinition {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(MyExtensionFunction2.class);

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("", "http://mytest/", "myExtensionFunction2");
    }

    @Override
    public int getMinimumNumberOfArguments() {
        return 1;
    }

    @Override
    public int getMaximumNumberOfArguments() {
        return 2;
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[]{SequenceType.SINGLE_STRING, SequenceType.OPTIONAL_STRING};
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        int resultCardinality = 1;
        return SequenceType.makeSequenceType(SequenceType.SINGLE_STRING.getPrimaryType(), resultCardinality);
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new ExtensionFunctionCall() {
            private static final long serialVersionUID = 1L;

            @Override
            public Sequence call(XPathContext xPathContext, Sequence[] arguments) throws XPathException {
                // 1st argument (mandatory, index 0)
                StringValue arg1 = (StringValue) arguments[0].iterate().next();
                String arg1Str = arg1.getStringValue();

                // 2nd argument (optional, index 1)
                String arg2Str = "";
                if (arguments.length > 1) {
                    StringValue arg2 = (StringValue) arguments[1].iterate().next();
                    arg2Str = arg2.getStringValue();
                }

                // Functionality goes here
                String resultStr = arg1Str + arg2Str;

                Item result = new StringValue(resultStr);
                return SequenceTool.toLazySequence(SingletonIterator.makeIterator(result));
            }
        };
    }

}
