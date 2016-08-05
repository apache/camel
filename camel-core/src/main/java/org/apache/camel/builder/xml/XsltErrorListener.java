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

import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ErrorListener} which logs the errors and rethrow the exceptions for error and fatal conditions.
 */
public class XsltErrorListener implements ErrorListener {

    private static final Logger LOG = LoggerFactory.getLogger(XsltErrorListener.class);

    @Override
    public void warning(TransformerException e) throws TransformerException {
        // just log warning
        LOG.warn("Warning parsing XSLT file: " + e.getMessageAndLocation());
    }

    @Override
    public void error(TransformerException e) throws TransformerException {
        LOG.error("Error parsing XSLT file: " + e.getMessageAndLocation());
        throw e;
    }

    @Override
    public void fatalError(TransformerException e) throws TransformerException {
        LOG.error("Fatal error parsing XSLT file: " + e.getMessageAndLocation());
        throw e;
    }
}
