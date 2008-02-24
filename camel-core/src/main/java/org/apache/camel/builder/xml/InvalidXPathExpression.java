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
package org.apache.camel.builder.xml;

import javax.xml.xpath.XPathException;

import org.apache.camel.RuntimeExpressionException;

/**
 * An exception thrown if am XPath expression could not be parsed or evaluated
 *
 * @version $Revision$
 */
public class InvalidXPathExpression extends RuntimeExpressionException {
    private final String xpath;

    public InvalidXPathExpression(String xpath, XPathException e) {
        super("Invalid xpath: " + xpath + ". Reason: " + e, e);
        this.xpath = xpath;
    }

    public String getXpath() {
        return xpath;
    }
}
