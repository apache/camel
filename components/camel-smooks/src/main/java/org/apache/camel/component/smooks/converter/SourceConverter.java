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
package org.apache.camel.component.smooks.converter;

import java.io.File;
import java.io.InputStream;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;
import org.smooks.io.payload.JavaResult;
import org.smooks.io.payload.JavaSource;
import org.smooks.io.payload.JavaSourceWithoutEventStream;

/**
 * SourceConverter is a Camel {@link Converter} that converts from different
 * formats to {@link Source} instances. </p>
 */
@Converter(generateLoader = true)
public class SourceConverter {
    private SourceConverter() {
    }

    @Converter
    public static JavaSourceWithoutEventStream toJavaSourceWithoutEventStream(Object payload) {
        return new JavaSourceWithoutEventStream(payload);
    }

    @Converter
    public static JavaSource toJavaSource(Object payload) {
        return new JavaSource(payload);
    }

    @Converter
    public static Source toStreamSource(InputStream in) {
        return new StreamSource(in);
    }

    @Converter
    public static JavaSource toJavaSource(JavaResult result) {
        return new JavaSource(result.getResultMap().values());
    }

    @Converter(allowNull = true)
    public static Source toStreamSource(WrappedFile<?> file, Exchange exchange) throws Exception {
        Object obj = file.getFile();
        if (obj instanceof File f) {
            return new StreamSource(f);
        } else {
            InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, file.getBody());
            return new StreamSource(is);
        }
    }

}
