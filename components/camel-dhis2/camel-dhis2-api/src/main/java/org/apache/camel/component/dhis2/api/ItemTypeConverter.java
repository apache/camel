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
package org.apache.camel.component.dhis2.api;

import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;

public class ItemTypeConverter implements TypeConverter {

    private final Dhis2Client dhis2Client;

    public ItemTypeConverter(Dhis2Client dhis2Client) {
        this.dhis2Client = dhis2Client;
    }

    @Override
    public boolean allowNull() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T convertTo(Class<T> type, Object value) throws TypeConversionException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T mandatoryConvertTo(Class<T> type, Object value)
            throws TypeConversionException, NoTypeConversionAvailableException {
        return (T) dhis2Client.getConverterFactory().createConverter().convert(value).getBytes();
    }

    @Override
    public <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value)
            throws TypeConversionException, NoTypeConversionAvailableException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T tryConvertTo(Class<T> type, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T tryConvertTo(Class<T> type, Exchange exchange, Object value) {
        throw new UnsupportedOperationException();
    }
}
