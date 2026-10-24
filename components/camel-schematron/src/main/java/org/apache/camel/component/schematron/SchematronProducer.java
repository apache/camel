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
package org.apache.camel.component.schematron;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.Source;

import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.schematron.constant.Constants;
import org.apache.camel.component.schematron.exception.SchematronValidationException;
import org.apache.camel.component.schematron.processor.SchematronProcessor;
import org.apache.camel.component.schematron.processor.SchematronProcessorFactory;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Schematron producer.
 */
public class SchematronProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SchematronProducer.class);
    private SchematronEndpoint endpoint;

    /**
     * @param endpoint the schematron endpoint.
     */
    public SchematronProducer(final SchematronEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    /**
     * Processes the payload. Validates the XML using the SchematronEngine
     *
     * @param  exchange
     * @throws Exception
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        final SchematronProcessor schematronProcessor = SchematronProcessorFactory.newSchematronEngine(endpoint.getRules());
        final Object payload = exchange.getIn().getBody();
        final String report;

        if (payload instanceof Source) {
            LOG.debug("Applying schematron validation on payload: {}", payload);
            report = schematronProcessor.validate((Source) payload);
        } else if (payload instanceof String) {
            LOG.debug("Applying schematron validation on payload: {}", payload);
            report = schematronProcessor.validate((String) payload);
        } else if (isBinary(payload) && ExchangeHelper.getCharsetName(exchange, false) == null) {
            // let the parser decode the bytes using the encoding of the XML declaration
            LOG.debug("Applying schematron validation on payload: {}", payload);
            InputStream is = exchange.getIn().getMandatoryBody(InputStream.class);
            try {
                report = schematronProcessor.validate(is);
            } finally {
                // close only the stream opened here, such as on a file, not a stream that is the body
                if (is != payload) {
                    IOHelper.close(is);
                }
            }
        } else {
            String stringPayload = exchange.getIn().getMandatoryBody(String.class);
            LOG.debug("Applying schematron validation on payload: {}", stringPayload);
            report = schematronProcessor.validate(stringPayload);
        }

        LOG.debug("Schematron validation report \n {}", report);
        String status = getValidationStatus(report);
        LOG.info("Schematron validation status : {}", status);
        setValidationReport(exchange, report, status);
    }

    /**
     * Whether the payload holds bytes the parser can read as they are
     */
    private static boolean isBinary(Object payload) {
        return payload instanceof byte[] || payload instanceof InputStream || payload instanceof StreamCache
                || payload instanceof File || payload instanceof Path || payload instanceof WrappedFile;
    }

    /**
     * Sets validation report and status
     */
    private void setValidationReport(Exchange exchange, String report, String status) {
        // if exchange pattern is In and Out set details on the Out message.
        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.VALIDATION_STATUS, status);
        headers.put(Constants.VALIDATION_REPORT, report);
        if (exchange.getPattern().isOutCapable()) {
            exchange.getMessage().setHeaders(exchange.getIn().getHeaders());
            exchange.getMessage().getHeaders().putAll(headers);
        } else {
            exchange.getIn().getHeaders().putAll(headers);
        }
    }

    /**
     * Get validation status, SUCCESS or FAILURE
     */
    private String getValidationStatus(final String report) {
        String status = report.contains(Constants.FAILED_ASSERT) ? Constants.FAILED : Constants.SUCCESS;
        if (this.endpoint.isAbort() && Constants.FAILED.equals(status)) {
            throw new SchematronValidationException("Schematron validation failure \n" + report);
        }
        return status;
    }

}
