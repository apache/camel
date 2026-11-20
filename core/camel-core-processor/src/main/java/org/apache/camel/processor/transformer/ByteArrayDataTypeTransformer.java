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

import java.io.File;
import java.nio.file.Path;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;

/**
 * Generic binary data type uses Camel message body converter mechanism to convert content to byte array representation.
 * A stream cache or a file body is binary already and can be read again, so it is kept as it is: a large body, such as
 * an object read from a bucket or a file to upload, is not read into memory. Any other stream is read into a byte
 * array, as it may be read only once.
 */
@DataTypeTransformer(name = "application-octet-stream")
@BodyTypes(transformerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.NULL },
                                     description = "A body Camel can convert to a byte array: a number fails, and a null"
                                                   + " body stays null"),
           transformerOut = @BodyType(value = { Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.NULL },
                                      description = "A stream cache or a file body is kept as it is and a null body"
                                                    + " stays null; any other body becomes a byte array"))
public class ByteArrayDataTypeTransformer extends Transformer {

    private static final Transformer DELEGATE = new TypeConverterTransformer(byte[].class);

    @Override
    public void transform(Message message, DataType from, DataType to) throws Exception {
        Object body = message.getBody();
        if (!(body instanceof StreamCache || body instanceof File || body instanceof Path
                || body instanceof WrappedFile)) {
            DELEGATE.transform(message, from, to);
        }

        message.setHeader(Exchange.CONTENT_TYPE, "application/octet-stream");
    }
}
