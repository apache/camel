/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.converter.xmlbeans;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.xmlbeans.XmlObject;

/**
 * @version $Revision: 1.1 $
 */
public class XmlBeansConverterTest extends ContextTestSupport {
    public void testConvertToXmlObject() throws Exception {
        Exchange exchange = createExchangeWithBody("<hello>world!</hello>");
        Message in = exchange.getIn();
        XmlObject object = in.getBody(XmlObject.class);
        assertNotNull("Should have created an XmlObject!", object);

        log.info("Found: " + object);
        assertEquals("body as String", in.getBody(String.class), object.toString());

    }
}
