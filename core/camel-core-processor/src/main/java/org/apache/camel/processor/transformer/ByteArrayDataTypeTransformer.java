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
package org.apache.camel.processor.transformer;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;

/**
 * Generic binary data type uses Camel message body converter mechanism to convert content to byte array representation.
 */
@DataTypeTransformer(name = "application-octet-stream")
public class ByteArrayDataTypeTransformer extends Transformer {

    private static final Transformer DELEGATE = new TypeConverterTransformer(byte[].class);

    @Override
    public void transform(Message message, DataType from, DataType to) throws Exception {
        DELEGATE.transform(message, from, to);

        message.setHeader(Exchange.CONTENT_TYPE, "application/octet-stream");
    }
}
