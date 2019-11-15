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
package org.apache.camel.component.spring.ws;

import java.util.Properties;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stax.StAXSource;

import org.apache.camel.support.builder.xml.StAX2SAXSource;
import org.apache.camel.support.builder.xml.XMLConverterHelper;
import org.apache.camel.util.ObjectHelper;

public final class SpringWebserviceHelper {

    private static String defaultCharset = ObjectHelper.getSystemProperty("org.apache.camel.default.charset", "UTF-8");

    private SpringWebserviceHelper() {
    }

    public static void toResult(Source source, Result result) throws TransformerException {
        if (source != null) {
            XMLConverterHelper xml = new XMLConverterHelper();
            TransformerFactory factory = xml.getTransformerFactory();
            Transformer transformer = factory.newTransformer();
            if (transformer == null) {
                throw new TransformerException("Could not create a transformer - JAXP is misconfigured!");
            } else {
                Properties outputProperties = new Properties();
                outputProperties.put("encoding", defaultCharset);
                outputProperties.put("omit-xml-declaration", "yes");

                transformer.setOutputProperties(outputProperties);
                if (factory.getClass().getName().equals("org.apache.xalan.processor.TransformerFactoryImpl") && source instanceof StAXSource) {
                    source = new StAX2SAXSource(((StAXSource)source).getXMLStreamReader());
                }

                transformer.transform(source, result);
            }
        }
    }

}
