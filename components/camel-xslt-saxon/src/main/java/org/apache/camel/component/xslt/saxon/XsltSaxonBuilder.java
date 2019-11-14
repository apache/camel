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

import javax.xml.transform.Source;
import javax.xml.transform.stax.StAXSource;

import org.apache.camel.component.xslt.XmlSourceHandlerFactoryImpl;
import org.apache.camel.component.xslt.XsltBuilder;
import org.apache.camel.support.builder.xml.StAX2SAXSource;

public class XsltSaxonBuilder extends XsltBuilder {

    private boolean allowStAX = true;

    @Override
    protected Source prepareSource(Source source) {
        if (!isAllowStAX() && source instanceof StAXSource) {
            // Always convert StAXSource to SAXSource.
            // * Xalan and Saxon-B don't support StAXSource.
            // * The JDK default implementation (XSLTC) doesn't handle CDATA events
            //   (see com.sun.org.apache.xalan.internal.xsltc.trax.StAXStream2SAX).
            // * Saxon-HE/PE/EE seem to support StAXSource, but don't advertise this
            //   officially (via TransformerFactory.getFeature(StAXSource.FEATURE))
            source = new StAX2SAXSource(((StAXSource) source).getXMLStreamReader());
        }
        return source;
    }

    // Properties
    // -------------------------------------------------------------------------

    public boolean isAllowStAX() {
        return allowStAX;
    }

    public void setAllowStAX(boolean allowStAX) {
        this.allowStAX = allowStAX;
    }

    @Override
    protected XmlSourceHandlerFactoryImpl createXmlSourceHandlerFactoryImpl() {
        return new SaxonXmlSourceHandlerFactoryImpl();
    }
}
