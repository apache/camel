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

package org.apache.camel.component.xslt.saxon;

import javax.xml.transform.TransformerFactory;

import net.sf.saxon.TransformerFactoryImpl;
import org.apache.camel.component.xslt.XsltAggregationStrategy;
import org.apache.camel.component.xslt.XsltBuilder;
import org.apache.camel.component.xslt.XsltOutput;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;

@Metadata(
        label = "bean",
        description =
                "The XSLT Aggregation Strategy enables you to use XSL stylesheets to aggregate messages (uses Saxon).",
        annotations = {"interfaceName=org.apache.camel.AggregationStrategy"})
@Configurer(metadataOnly = true)
public class XsltSaxonAggregationStrategy extends XsltAggregationStrategy {

    // need to duplicate fields for code generation purpose

    @Metadata(description = "The name of the XSL transformation file to use", required = true)
    private String xslFile;

    @Metadata(
            description = "The exchange property name that contains the XML payloads as an input",
            defaultValue = "new-exchange")
    private String propertyName;

    @Metadata(
            defaultValue = "string",
            enums = "string,bytes,DOM,file",
            description =
                    "Option to specify which output type to use. Possible values are: string, bytes, DOM, file. The first three"
                            + " options are all in memory based, where as file is streamed directly to a java.io.File. For file you must specify"
                            + " the filename in the IN header with the key XsltConstants.XSLT_FILE_NAME which is also CamelXsltFileName. Also any"
                            + " paths leading to the filename must be created beforehand, otherwise an exception is thrown at runtime.")
    private XsltOutput output = XsltOutput.string;

    @Metadata(
            label = "advanced",
            description = "To use a custom XSLT transformer factory, specified as a FQN class name")
    private String transformerFactoryClass;

    private TransformerFactory transformerFactory;

    public XsltSaxonAggregationStrategy(String xslFileLocation) {
        super(xslFileLocation);
        setTransformerFactory(new TransformerFactoryImpl());
    }

    @Override
    protected XsltBuilder createXsltBuilder() {
        XsltSaxonBuilder answer = getCamelContext().getInjector().newInstance(XsltSaxonBuilder.class);
        answer.setAllowStAX(true);
        return answer;
    }
}
