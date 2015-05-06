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
package org.apache.camel.converter.dozer;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.support.TypeConverterSupport;
import org.dozer.DozerBeanMapper;
import org.dozer.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>DozerTypeConverter</code> is a standard {@link TypeConverter} that
 * delegates to a {@link Mapper} from the Dozer framework to convert between
 * types. <code>DozerTypeConverter</code>s are created and installed into a
 * {@link CamelContext} by an instance of {@link DozerTypeConverterLoader}.
 * <p>
 * See <a href="http://dozer.sourceforge.net">dozer project page</a> or more information on configuring Dozer
 *
 * @see DozerTypeConverterLoader
 */
public class DozerTypeConverter extends TypeConverterSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DozerTypeConverter.class);
    
    private final DozerBeanMapper mapper;

    public DozerTypeConverter(DozerBeanMapper mapper) {
        this.mapper = mapper;
    }

    public DozerBeanMapper getMapper() {
        return mapper;
    }

    @Override
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
        // if the exchange is null, we have no chance to ensure that the TCCL is the one from the CamelContext
        if (exchange == null) {
            return mapper.map(value, type);
        }
        
        T answer = null;

        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        ClassLoader contextCl = exchange.getContext().getApplicationContextClassLoader();
        if (contextCl != null) {
            // otherwise, we ensure that the TCCL is the correct one
            LOG.debug("Switching TCCL to: {}.", contextCl);
            try {
                Thread.currentThread().setContextClassLoader(contextCl);
                answer = mapper.map(value, type);
            } finally {
                LOG.debug("Restored TCCL to: {}.", prev);
                Thread.currentThread().setContextClassLoader(prev);
            }
        } else {
            // just try with the current TCCL as-is
            answer = mapper.map(value, type);
        }

        return answer;
    }

}
