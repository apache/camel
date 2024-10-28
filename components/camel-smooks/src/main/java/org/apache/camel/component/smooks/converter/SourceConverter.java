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
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.camel.Converter;
import org.apache.camel.WrappedFile;
import org.smooks.api.SmooksException;
import org.smooks.api.io.Source;
import org.smooks.io.sink.JavaSink;
import org.smooks.io.source.JavaSource;
import org.smooks.io.source.JavaSourceWithoutEventStream;
import org.smooks.io.source.StreamSource;
import org.smooks.io.source.StringSource;
import org.smooks.io.source.URLSource;

/**
 * SourceConverter is a Camel {@link Converter} that converts from different formats to {@link Source} instances.
 * </p>
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
    public static StreamSource toStreamSource(InputStream in) {
        return new StreamSource<>(in);
    }

    @Converter
    public static JavaSource toJavaSource(JavaSink result) {
        return new JavaSource(result.getResultMap().values());
    }

    @Converter
    public static StringSource toStringSource(String string) {
        return new StringSource(string);
    }

    @Converter
    public static Source toURISource(WrappedFile<File> genericFile) {
        String systemId = new javax.xml.transform.stream.StreamSource((File) genericFile.getBody()).getSystemId();
        try {
            return new URLSource(new URL(systemId));
        } catch (MalformedURLException e) {
            throw new SmooksException(e);
        }
    }
}
