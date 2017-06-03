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
package org.apache.camel.component.schematron;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.schematron.constant.Constants;
import org.apache.camel.component.schematron.exception.SchematronValidationException;
import org.apache.camel.component.schematron.processor.SchematronProcessorFactory;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Schematron producer.
 */
public class SchematronProducer extends DefaultProducer {
    private Logger logger = LoggerFactory.getLogger(SchematronProducer.class);
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
     * @param exchange
     * @throws Exception
     */
    public void process(Exchange exchange) throws Exception {
        String payload = exchange.getIn().getBody(String.class);
        logger.debug("Applying schematron validation on payload: {}", payload);
        String report = SchematronProcessorFactory.newScehamtronEngine(endpoint.getRules()).validate(payload);
        logger.debug("Schematron validation report \n {}", report);
        String status = getValidationStatus(report);
        logger.info("Schematron validation status : {}", status);
        setValidationReport(exchange, report, status);
    }

    /**
     * Sets validation report and status
     *
     * @param exchange
     * @param report
     * @param status
     */
    private void setValidationReport(Exchange exchange, String report, String status) {
        // if exchange pattern is In and Out set details on the Out message.
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Constants.VALIDATION_STATUS, status);
        headers.put(Constants.VALIDATION_REPORT, report);
        if (exchange.getPattern().isOutCapable()) {
            exchange.getOut().setHeaders(exchange.getIn().getHeaders());
            exchange.getOut().getHeaders().putAll(headers);
        } else {
            exchange.getIn().getHeaders().putAll(headers);
        }
    }

    /**
     * Get validation status, SUCCESS or FAILURE
     *
     * @param report
     * @return
     */
    private String getValidationStatus(final String report) {
        String status = report.contains(Constants.FAILED_ASSERT) ? Constants.FAILED : Constants.SUCCESS;
        if (this.endpoint.isAbort() && Constants.FAILED.equals(status)) {
            throw new SchematronValidationException("Schematron validation failure \n" + report);
        }
        return status;
    }

}
