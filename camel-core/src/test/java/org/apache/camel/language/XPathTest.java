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
package org.apache.camel.language;

import javax.xml.xpath.XPathConstants;

import org.apache.camel.LanguageTestSupport;
import org.apache.camel.language.xpath.XPathLanguage;
import org.apache.camel.spi.Language;
import org.junit.Test;

/**
 * @version 
 */
public class XPathTest extends LanguageTestSupport {

    @Test
    public void testExpressions() throws Exception {
        assertExpression("in:body()", "<hello id='m123'>world!</hello>");
        assertExpression("in:header('foo')", "abc");
        assertExpression("$foo", "abc");
    }

    @Test
    public void testPredicates() throws Exception {
        assertPredicate("in:header('foo') = 'abc'");
        assertPredicate("$foo = 'abc'");
        assertPredicate("$foo = 'bar'", false);
    }

    @Test
    public void testNormalXPathExpression() throws Exception {
        assertExpression("hello", "world!");
        assertExpression("hello/text()", "world!");
        assertExpression("hello/@id", "m123");
    }

    protected String getLanguageName() {
        return "xpath";
    }

    @Override
    protected Language assertResolveLanguage(String languageName) {
        XPathLanguage answer = new XPathLanguage();
        answer.setResultType(XPathConstants.STRING);
        assertEquals(XPathConstants.STRING, answer.getResultType());
        return answer;
    }
}
