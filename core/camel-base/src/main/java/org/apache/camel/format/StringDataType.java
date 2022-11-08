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

package org.apache.camel.format;

import org.apache.camel.Exchange;
import org.apache.camel.spi.InputType;
import org.apache.camel.spi.OutputType;
import org.apache.camel.spi.annotations.DataType;

/**
 * Basic String data type implementation converts exchange message body content to String.
 */
@DataType(scheme = "camel", name = "string")
public class StringDataType implements InputType, OutputType {

    @Override
    public void convertIn(Exchange exchange) {
        convert(exchange);
    }

    @Override
    public void convertOut(Exchange exchange) {
        convert(exchange);
    }

    private void convert(Exchange exchange) {
        if (exchange.getIn().getBody() instanceof String) {
            return;
        }

        // Use default Camel converter utils to convert body to String
        exchange.getIn().setBody(exchange.getIn().getBody(String.class));
    }
}
