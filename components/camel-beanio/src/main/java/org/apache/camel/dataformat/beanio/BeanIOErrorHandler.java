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

import org.beanio.BeanReaderErrorHandlerSupport;
import org.beanio.InvalidRecordException;
import org.beanio.UnexpectedRecordException;
import org.beanio.UnidentifiedRecordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeanIOErrorHandler extends BeanReaderErrorHandlerSupport {

    private static final String LOG_PREFIX = "BeanIO: ";
    private static final Logger LOG = LoggerFactory.getLogger(BeanIOErrorHandler.class);

    private final BeanIOConfiguration configuration;

    public BeanIOErrorHandler(BeanIOConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void invalidRecord(InvalidRecordException ex) throws Exception {
        String msg = LOG_PREFIX + "InvalidRecord: " + ex.getMessage() + ": " + ex.getRecordContext().getRecordText();
        if (configuration.isIgnoreInvalidRecords()) {
            LOG.debug(msg);
        } else {
            LOG.warn(msg);
            throw ex;
        }
    }

    @Override
    public void unexpectedRecord(UnexpectedRecordException ex) throws Exception {
        String msg = LOG_PREFIX + "UnexpectedRecord: " + ex.getMessage() + ": " + ex.getRecordContext().getRecordText();
        if (configuration.isIgnoreUnexpectedRecords()) {
            LOG.debug(msg);
        } else {
            LOG.warn(msg);
            throw ex;
        }
    }

    @Override
    public void unidentifiedRecord(UnidentifiedRecordException ex) throws Exception {
        String msg = LOG_PREFIX + "UnidentifiedRecord: " + ex.getMessage() + ": " + ex.getRecordContext().getRecordText();
        if (configuration.isIgnoreUnidentifiedRecords()) {
            LOG.debug(msg);
        } else {
            LOG.warn(msg);
            throw ex;
        }
    }

}
