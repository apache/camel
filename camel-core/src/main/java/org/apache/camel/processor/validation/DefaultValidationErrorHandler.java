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
package org.apache.camel.processor.validation;

import org.xml.sax.SAXParseException;
import org.xml.sax.SAXException;
import org.apache.camel.Exchange;

import javax.xml.transform.dom.DOMResult;
import javax.xml.validation.Schema;
import java.util.List;
import java.util.ArrayList;

/**
 * @version $Revision: $
 */
public class DefaultValidationErrorHandler implements ValidatorErrorHandler {
    private List<SAXParseException> warnings = new ArrayList<SAXParseException>();
    private List<SAXParseException> errors = new ArrayList<SAXParseException>();
    private List<SAXParseException> fatalErrors = new ArrayList<SAXParseException>();


    public void warning(SAXParseException e) throws SAXException {
        warnings.add(e);
    }

    public void error(SAXParseException e) throws SAXException {
        errors.add(e);
    }

    public void fatalError(SAXParseException e) throws SAXException {
        fatalErrors.add(e);
    }

    public void reset() {
    }

    public boolean isValid() {
        return errors.isEmpty() && fatalErrors.isEmpty();
    }

    public void handleErrors(Exchange exchange, Schema schema, DOMResult result) throws ValidationException {
        if (!isValid()) {
            throw new SchemaValidationException(exchange, schema, fatalErrors, errors, warnings);
        }
    }
}
