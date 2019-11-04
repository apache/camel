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
package org.apache.camel.component.nitrite.operation.common;

import java.io.ByteArrayOutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.component.nitrite.AbstractNitriteOperation;
import org.apache.camel.component.nitrite.NitriteEndpoint;
import org.apache.camel.component.nitrite.operation.CommonOperation;
import org.dizitart.no2.tool.ExportOptions;
import org.dizitart.no2.tool.Exporter;

/**
 * Export full database to JSON and stores result in body - see Nitrite docs for details about format
 */
public class ExportDatabaseOperation extends AbstractNitriteOperation implements CommonOperation {
    private ExportOptions options;

    public ExportDatabaseOperation() {
    }

    public ExportDatabaseOperation(ExportOptions options) {
        this.options = options;
    }

    @Override
    protected void execute(Exchange exchange, NitriteEndpoint endpoint) throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Exporter exporter = Exporter.of(endpoint.getNitriteDatabase());
        if (options != null) {
            exporter.withOptions(options);
        }
        exporter.exportTo(stream);
        exchange.getMessage().setBody(stream.toByteArray());
    }
}
