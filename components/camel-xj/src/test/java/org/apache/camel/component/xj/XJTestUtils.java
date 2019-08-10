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

package org.apache.camel.component.xj;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.skyscreamer.jsonassert.JSONAssert;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.*;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class XJTestUtils {

    private XJTestUtils() {
    }

    public static final TransformerFactory TRANSFORMER_FACTORY = TransformerFactory.newInstance();
    public static final Map<String, Templates> XSL_TEMPLATES = Collections.synchronizedMap(new HashMap<>());

    public static final JsonFactory JSON_FACTORY = new JsonFactory();

    public static Transformer getTransformer() throws Exception {
        final Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
        setPrettyPrinting(transformer);

        return transformer;
    }

    public static Transformer getTransformer(String xsl) throws TransformerException {
        final Transformer transformer;

        if (XSL_TEMPLATES.containsKey(xsl)) {
            transformer = XSL_TEMPLATES.get(xsl).newTransformer();
        } else {
            final InputStream xslInputStream = XJTestUtils.class.getClassLoader().getResourceAsStream(xsl);
            final Templates templates = TRANSFORMER_FACTORY.newTemplates(new StreamSource(xslInputStream));
            XSL_TEMPLATES.put(xsl, templates);

            transformer = templates.newTransformer();
        }

        setPrettyPrinting(transformer);

        return transformer;
    }

    public static void setPrettyPrinting(Transformer transformer) {
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    }

    public static void transformXml2JsonAndCompare(String xsl, String testName) throws Exception {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final JsonGenerator jsonGenerator = JSON_FACTORY.createGenerator(byteArrayOutputStream);

        final XmlJsonStreamWriter xmlStreamWriter = new XmlJsonStreamWriter(jsonGenerator);
        final StAXResult stAXResult = new StAXResult(xmlStreamWriter);

        final InputStream inputFile = XJTestUtils.class.getClassLoader().getResourceAsStream(testName + ".xml");
        if (inputFile == null) {
            throw new IOException(testName + ".xml not found");
        }

        final InputStream referenceFile = XJTestUtils.class.getClassLoader().getResourceAsStream(testName + ".json");
        if (referenceFile == null) {
            throw new IOException(testName + ".json not found");
        }

        final Transformer transformer;
        if (xsl == null) {
            transformer = getTransformer();
        } else {
            transformer = getTransformer(xsl);
        }
        try {
            transformer.transform(new StreamSource(inputFile), stAXResult);
        } catch (Exception e) {
            String result = byteArrayOutputStream.toString("UTF-8");
            System.out.println(result);
            throw e;
        }

        final String expected = IOUtils.toString(referenceFile, "UTF-8");
        final String result = byteArrayOutputStream.toString("UTF-8");

        JSONAssert.assertEquals(result, expected, result, true);
    }

    public static void transformJson2XmlAndCompare(String xsl, String testName) throws Exception {

        final InputStream inputFile = XJTestUtils.class.getClassLoader().getResourceAsStream(testName + ".json");
        if (inputFile == null) {
            throw new IOException(testName + ".json not found");
        }

        final InputStream referenceFile = XJTestUtils.class.getClassLoader().getResourceAsStream(testName + ".xml");
        if (referenceFile == null) {
            throw new IOException(testName + ".xml not found");
        }

        final JsonParser jsonParser = JSON_FACTORY.createParser(inputFile);

        final XMLStreamReader xmlStreamReader = new JsonXmlStreamReader(jsonParser);

        final StAXSource stAXSource = new StAXSource(xmlStreamReader);

        final Transformer transformer;
        if (xsl == null) {
            transformer = getTransformer();
        } else {
            transformer = getTransformer(xsl);
        }

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        transformer.transform(stAXSource, new StreamResult(byteArrayOutputStream));

        final String expected = IOUtils.toString(referenceFile, "UTF-8");
        final String result = byteArrayOutputStream.toString("UTF-8");

        final Diff diff = DiffBuilder
                .compare(Input.fromString(expected))
                .withTest(Input.fromString(result))
                .ignoreElementContentWhitespace()
                .checkForIdentical()
                .build();

        Assert.assertFalse("\nExpected: " + expected + "\n\nGot: " + result + "\n\nDiff: " + diff.toString(), diff.hasDifferences());
    }
}
