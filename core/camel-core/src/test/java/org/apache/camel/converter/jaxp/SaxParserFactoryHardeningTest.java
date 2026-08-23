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

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SAX factory and the DOM factory in {@link XmlConverter} are both reachable from a converted message body, so they
 * must not disagree about external-resource resolution. The DOM factory has always blocked it; the SAX factory only
 * blocked general entities.
 * <p/>
 * These assertions are on the factory configuration rather than on parse behaviour deliberately: whether a given JDK
 * would have fetched the resource anyway varies by version, and the point is that Camel states the intent itself.
 */
class SaxParserFactoryHardeningTest {

    private static final String EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
    private static final String LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    @Test
    void saxFactoryBlocksExternalResourceResolution() throws Exception {
        SAXParserFactory factory = new XmlConverter().createSAXParserFactory();

        assertThat(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING)).isTrue();
        assertThat(factory.getFeature(EXTERNAL_GENERAL_ENTITIES)).isFalse();
        assertThat(factory.getFeature(EXTERNAL_PARAMETER_ENTITIES)).isFalse();
        assertThat(factory.getFeature(LOAD_EXTERNAL_DTD)).isFalse();
    }

    @Test
    void saxFactoryAgreesWithTheDomFactoryOnSharedFeatures() throws Exception {
        SAXParserFactory sax = new XmlConverter().createSAXParserFactory();
        var dom = new XmlConverter().createDocumentBuilderFactory();

        assertThat(sax.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING))
                .isEqualTo(dom.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        assertThat(sax.getFeature(EXTERNAL_GENERAL_ENTITIES))
                .isEqualTo(dom.getFeature(EXTERNAL_GENERAL_ENTITIES));
    }
}
