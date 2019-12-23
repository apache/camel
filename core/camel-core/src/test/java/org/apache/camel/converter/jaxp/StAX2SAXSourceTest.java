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
package org.apache.camel.converter.jaxp;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.Charset;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.builder.xml.StAX2SAXSource;
import org.junit.Test;

import static org.hamcrest.core.IsEqual.equalTo;

public class StAX2SAXSourceTest extends ContextTestSupport {

    private static final String TEST_XML = "<root xmlns=\"urn:org.apache.camel:test\">Text</root>";

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    @Test
    public void testDefaultPrefixInRootElementWithCopyTransformer() throws Exception {
        TransformerFactory trf = TransformerFactory.newInstance();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XMLStreamReader reader = context.getTypeConverter().mandatoryConvertTo(XMLStreamReader.class, new StringReader(TEST_XML));
        // ensure UTF-8 encoding
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(Exchange.CHARSET_NAME, UTF_8.toString());
        XMLStreamWriter writer = context.getTypeConverter().mandatoryConvertTo(XMLStreamWriter.class, exchange, baos);
        StAX2SAXSource staxSource = new StAX2SAXSource(reader);
        StreamSource templateSource = new StreamSource(getClass().getResourceAsStream("/xslt/common/copy.xsl"));
        Transformer transformer = trf.newTransformer(templateSource);
        log.info("Used transformer: {}", transformer.getClass().getName());
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(staxSource, new StreamResult(baos));
        writer.flush();
        baos.flush();
        assertThat(new String(baos.toByteArray()), equalTo(TEST_XML));
    }

}
