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

package org.apache.camel.component.aws2.s3.format;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.spi.OutputType;
import org.apache.camel.spi.annotations.DataType;
import software.amazon.awssdk.utils.IoUtils;

@DataType(scheme = "aws2-s3", name = "binary")
public class AWS2S3BinaryDataType implements OutputType {

    @Override
    public void convertOut(Exchange exchange) {
        if (exchange.getIn().getBody() instanceof byte[]) {
            return;
        }

        InputStream is = exchange.getIn().getBody(InputStream.class);
        if (is != null) {
            try {
                exchange.getIn().setBody(IoUtils.toByteArray(is));
                return;
            } catch (IOException e) {
                throw new CamelExecutionException("Failed to convert AWS S3 body to byte[]", exchange, e);
            }
        }

        // Use default Camel converter utils to convert body to byte[]
        exchange.getIn().setBody(exchange.getIn().getBody(byte[].class));
    }
}
