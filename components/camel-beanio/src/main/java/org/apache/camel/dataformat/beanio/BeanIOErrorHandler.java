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
package org.apache.camel.dataformat.beanio;

import org.apache.camel.Exchange;
import org.beanio.BeanReaderErrorHandler;
import org.beanio.BeanReaderErrorHandlerSupport;
import org.beanio.InvalidRecordException;
import org.beanio.UnexpectedRecordException;
import org.beanio.UnidentifiedRecordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link BeanReaderErrorHandler} to handle errors during parsing. This error handler is prototype scoped
 * and provides access to the current {@link Exchange}.
 * You can perform any custom initialization logic in the {@link #init()} method.
 */
public class BeanIOErrorHandler extends BeanReaderErrorHandlerSupport {

    static final String LOG_PREFIX = "BeanIO: ";
    static final Logger LOG = LoggerFactory.getLogger(BeanIOErrorHandler.class);

    private BeanIOConfiguration configuration;
    private Exchange exchange;

    public BeanIOErrorHandler() {
    }

    public void init() {
        // any custom init code here
    }

    public BeanIOConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(BeanIOConfiguration configuration) {
        this.configuration = configuration;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public void invalidRecord(InvalidRecordException ex) throws Exception {
        String msg = LOG_PREFIX + "InvalidRecord: " + ex.getMessage() + ": " + ex.getRecordContext().getRecordText();
        if (getConfiguration().isIgnoreInvalidRecords()) {
            LOG.debug(msg);
        } else {
            LOG.warn(msg);
            throw ex;
        }
    }

    @Override
    public void unexpectedRecord(UnexpectedRecordException ex) throws Exception {
        String msg = LOG_PREFIX + "UnexpectedRecord: " + ex.getMessage() + ": " + ex.getRecordContext().getRecordText();
        if (getConfiguration().isIgnoreUnexpectedRecords()) {
            LOG.debug(msg);
        } else {
            LOG.warn(msg);
            throw ex;
        }
    }

    @Override
    public void unidentifiedRecord(UnidentifiedRecordException ex) throws Exception {
        String msg = LOG_PREFIX + "UnidentifiedRecord: " + ex.getMessage() + ": " + ex.getRecordContext().getRecordText();
        if (getConfiguration().isIgnoreUnidentifiedRecords()) {
            LOG.debug(msg);
        } else {
            LOG.warn(msg);
            throw ex;
        }
    }

}
