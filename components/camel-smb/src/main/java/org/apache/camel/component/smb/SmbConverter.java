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
package org.apache.camel.component.smb;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;

@Converter(generateLoader = true)
public class SmbConverter {

    private SmbConverter() {
        // Helper Class
    }

    @Converter(allowNull = true)
    // NOTE: the client must close the stream returned.
    public static InputStream smbToInputStream(SmbFile file, Exchange exchange) throws Exception {
        // allow null as valid response, because camel-smb can be set to download=false
        Object body = file.getBody();
        if (body == null) {
            return null;
        }
        if (body instanceof InputStream is) {
            return is;
        } else if (body instanceof byte[] arr) {
            return new ByteArrayInputStream(arr);
        } else {
            return exchange.getContext().getTypeConverter().convertTo(InputStream.class, exchange, body);
        }
    }

}
