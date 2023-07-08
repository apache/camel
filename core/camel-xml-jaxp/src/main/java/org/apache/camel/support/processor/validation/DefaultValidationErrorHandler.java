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
package org.apache.camel.support.processor.validation;

import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.Result;
import javax.xml.validation.Schema;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import org.apache.camel.Exchange;
import org.apache.camel.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default error handler which just stores all the errors so they can be reported or transformed.
 */
public class DefaultValidationErrorHandler implements ValidatorErrorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultValidationErrorHandler.class);
    private final List<SAXParseException> warnings = new ArrayList<>();
    private final List<SAXParseException> errors = new ArrayList<>();
    private final List<SAXParseException> fatalErrors = new ArrayList<>();

    @Override
    public void warning(SAXParseException e) throws SAXException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Validation warning: {}", e, e);
        }
        warnings.add(e);
    }

    @Override
    public void error(SAXParseException e) throws SAXException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Validation error: {}", e, e);
        }
        errors.add(e);
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Validation fatalError: {}", e, e);
        }
        fatalErrors.add(e);
    }

    @Override
    public void reset() {
        warnings.clear();
        errors.clear();
        fatalErrors.clear();
    }

    public boolean isValid() {
        return errors.isEmpty() && fatalErrors.isEmpty();
    }

    @Override
    public void handleErrors(Exchange exchange, Schema schema, Result result) throws ValidationException {
        if (!isValid()) {
            throw new SchemaValidationException(exchange, schema, fatalErrors, errors, warnings);
        }
    }

    public void handleErrors(Exchange exchange, Object schema) throws ValidationException {
        if (!isValid()) {
            throw new SchemaValidationException(exchange, schema, fatalErrors, errors, warnings);
        }
    }
}
