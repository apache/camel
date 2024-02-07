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
package org.apache.camel.language;

import java.util.Iterator;

import org.apache.camel.model.language.XQueryExpression;

/**
 * Ensures that the "xquery" language is compliant with the single input expectations.
 */
class XQueryLanguageTest extends AbstractSingleInputTypedLanguageTest<XQueryExpression.Builder, XQueryExpression> {

    XQueryLanguageTest() {
        super("/foo/text()", factory -> factory.xquery().resultType(String.class));
    }

    @Override
    protected Object defaultContentToSend() {
        return "<foo>John</foo>";
    }

    @Override
    protected TestContext testWithTypeContext() {
        return new TestContext(defaultContentToSend(), "John", String.class);
    }

    @Override
    protected TestContext testWithoutTypeContext() {
        return new TestContext(defaultContentToSend(), "John", null);
    }

    @Override
    protected void assertTypeInstanceOf(Class<?> expected, Object body) {
        // noop
    }

    @Override
    protected void assertBodyReceived(Object expected, Object body) {
        // uses an iterator, so we need to walk it to get the body
        if (body instanceof Iterator<?> it) {
            body = it.next();
        }
        super.assertBodyReceived(expected, body);
    }

}
