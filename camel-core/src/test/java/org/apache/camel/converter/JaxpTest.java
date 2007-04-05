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
package org.apache.camel.converter;

import junit.framework.TestCase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.w3c.dom.Document;

import javax.xml.transform.Source;

/**
 * @version $Revision$
 */
public class JaxpTest extends TestCase {
    private static final transient Log log = LogFactory.getLog(JaxpTest.class);

    protected TypeConverter converter = new DefaultTypeConverter();

    public void testConvertToDocument() throws Exception {
        Document document = converter.convertTo(Document.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><hello>world!</hello>");
        assertNotNull(document);

        log.debug("Found document: " + document);

        // lets now convert back again

        String text = converter.convertTo(String.class, document);
        // The preamble changes a little under Java 1.6 it adds a standalone="no" attribute.
        assertTrue("Converted to String", text.endsWith("?><hello>world!</hello>"));
    }

    public void testConvertToSource() throws Exception {
        Source source = converter.convertTo(Source.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><hello>world!</hello>");
        assertNotNull(source);

        log.debug("Found document: " + source);
    }
}
