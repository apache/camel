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
package org.apache.camel.component.fastjson.converter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

import com.alibaba.fastjson.JSON;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.spi.TypeConverterRegistry;

/**
 * FastJson {@link org.apache.camel.TypeConverter} that allows converting json to byte buffer/stream oriented output. <br/>
 * This implementation uses a fallback converter.
 */
@Converter(generateLoader = true)
public final class FastJsonTypeConverters {

    public FastJsonTypeConverters() {
    }

    @Converter(fallback = true)
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value, TypeConverterRegistry registry) throws Exception {
        if (ByteBuffer.class.isAssignableFrom(type)) {
            byte[] out = JSON.toJSONBytes(value);
            return type.cast(ByteBuffer.wrap(out));
        } else if (InputStream.class.isAssignableFrom(type)) {
            byte[] out = JSON.toJSONBytes(value);
            return type.cast(new ByteArrayInputStream(out));
        }

        return null;
    }

}
