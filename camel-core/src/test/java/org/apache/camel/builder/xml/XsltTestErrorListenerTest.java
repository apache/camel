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

import java.net.URL;
import javax.xml.transform.ErrorListener;

import junit.framework.TestCase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class XsltTestErrorListenerTest extends TestCase {

    private XsltBuilder xsltBuilder = new XsltBuilder();
    private ErrorListener errorListener = mock(ErrorListener.class);

    public void testErrorListener() throws Exception {
        // Xalan transformer cannot work as expected, so we just skip the test
        if (xsltBuilder.getConverter().getTransformerFactory().getClass().getName().startsWith("org.apache.xalan")) {
            return;
        }

        URL styleSheet = getClass().getResource("example-with-errors.xsl");
        try {
            xsltBuilder.setErrorListener(errorListener);
            xsltBuilder.setTransformerURL(styleSheet);
            fail("Should throw exception");
        } catch (Exception ex) {
            // expected
        }
        verify(errorListener, atLeastOnce()).error(any());
        verify(errorListener).fatalError(any());
    }
}
