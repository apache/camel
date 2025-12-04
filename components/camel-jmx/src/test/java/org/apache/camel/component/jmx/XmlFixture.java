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

package org.apache.camel.component.jmx;

import java.io.File;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.xmlunit.assertj3.XmlAssert;
import org.xmlunit.builder.Input;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class XmlFixture {

    private static final Logger LOG = LoggerFactory.getLogger(XmlFixture.class);

    private XmlFixture() {}

    public static Source toSource(String aXmlString) {
        return Input.fromString(aXmlString).build();
    }

    public static Source toSource(File aFile) {
        return Input.fromFile(aFile).build();
    }

    public static void assertXMLIgnorePrefix(String aMessage, Source aExpected, Source aActual) throws Exception {
        XmlAssert.assertThat(aExpected)
                .and(aActual)
                .ignoreComments()
                .ignoreWhitespace()
                .areSimilar();
    }

    public static Source stripTimestamp(Source aSource) throws Exception {
        String resourcePath = "/stripTimestamp.xsl";
        return transform(aSource, resourcePath);
    }

    public static Source stripUUID(Source aSource) throws Exception {
        String resourcePath = "/stripUUID.xsl";
        return transform(aSource, resourcePath);
    }

    protected static Source transform(Source aSource, String aResourcePath) {
        Source stylesheet = new StreamSource(XmlFixture.class.getResourceAsStream(aResourcePath));
        stylesheet.setSystemId(XmlFixture.class.getResource(aResourcePath).toExternalForm());
        return Input.byTransforming(aSource).withStylesheet(stylesheet).build();
    }
}
