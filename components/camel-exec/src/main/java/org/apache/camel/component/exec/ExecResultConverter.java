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
package org.apache.camel.component.exec;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.w3c.dom.Document;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.TypeConverter.MISS_VALUE;

/**
 * Default converters for {@link ExecResult}. For details how to extend the
 * converters check out <a
 * href="http://camel.apache.org/type-converter.html">the Camel docs for type
 * converters.</a>
 */
@Converter(generateLoader = true)
public final class ExecResultConverter {

    private static final Logger LOG = LoggerFactory.getLogger(ExecResultConverter.class);

    private ExecResultConverter() {
    }

    @Converter
    public static InputStream convertToInputStream(ExecResult result) throws FileNotFoundException {
        return toInputStream(result);
    }

    @Converter
    public static byte[] convertToByteArray(ExecResult result, Exchange exchange) throws FileNotFoundException, IOException {
        try (InputStream stream = toInputStream(result)) {
            return IOUtils.toByteArray(stream);
        }
    }

    @Converter
    public static String convertToString(ExecResult result, Exchange exchange) throws FileNotFoundException {
        // special for string, as we want an empty string if no output from stdin / stderr
        InputStream is = toInputStream(result);
        if (is != null) {
            return exchange.getContext().getTypeConverter().convertTo(String.class, exchange, is);
        } else {
            // no stdin/stdout, so return an empty string
            return "";
        }
    }

    @Converter
    public static Document convertToDocument(ExecResult result, Exchange exchange) throws FileNotFoundException {
        return convertTo(Document.class, exchange, result);
    }

    /**
     * Converts <code>ExecResult</code> to the type <code>T</code>.
     *
     * @param <T>      The type to convert to
     * @param type     Class instance of the type to which to convert
     * @param exchange a Camel exchange. If exchange is <code>null</code>, no
     *                 conversion will be made
     * @param result   the exec result
     * @return the converted {@link ExecResult}
     * @throws FileNotFoundException if there is a file in the execResult, and
     *                               the file can not be found
     */
    @SuppressWarnings("unchecked")
    private static <T> T convertTo(Class<T> type, Exchange exchange, ExecResult result) throws FileNotFoundException {
        InputStream is = toInputStream(result);
        if (is != null) {
            return exchange.getContext().getTypeConverter().convertTo(type, exchange, is);
        } else {
            // use Void to indicate we cannot convert it
            // (prevents Camel from using a fallback converter which may convert a String from the instance name)  
            return (T) MISS_VALUE;
        }
    }

    /**
     * Returns <code>InputStream</code> object with the <i>output</i> of the
     * executable. If there is {@link ExecCommand#getOutFile()}, its content is
     * preferred to {@link ExecResult#getStdout()}. If no out file is set, and
     * the stdout of the exec result is <code>null</code> returns the stderr of
     * the exec result. <br>
     * If the output stream is of type <code>ByteArrayInputStream</code>, its
     * <code>reset()</code> method is called.
     *
     * @param execResult ExecResult object to convert to InputStream.
     * @return InputStream object with the <i>output</i> of the executable.
     * Returns <code>null</code> if both {@link ExecResult#getStdout()}
     * and {@link ExecResult#getStderr()} are <code>null</code> , or if
     * the <code>execResult</code> is <code>null</code>.
     * @throws FileNotFoundException if the {@link ExecCommand#getOutFile()} can
     *                               not be opened. In this case the out file must have had a not
     *                               <code>null</code> value
     */
    private static InputStream toInputStream(ExecResult execResult) throws FileNotFoundException {
        if (execResult == null) {
            LOG.warn("Received a null ExecResult instance to convert!");
            return null;
        }
        // prefer the out file for output
        InputStream result;
        if (execResult.getCommand().getOutFile() != null) {
            result = new FileInputStream(execResult.getCommand().getOutFile());
        } else {
            // if the stdout is null, return the stderr.
            if (execResult.getStdout() == null && execResult.getCommand().isUseStderrOnEmptyStdout()) {
                LOG.warn("ExecResult has no stdout, will fallback to use stderr.");
                result = execResult.getStderr();
            } else {
                result = execResult.getStdout();
            }
        }
        // reset the stream if it was already read.
        resetIfByteArrayInputStream(result);
        return result;
    }

    /**
     * Resets the stream, only if it's a ByteArrayInputStream.
     */
    private static void resetIfByteArrayInputStream(InputStream stream) {
        if (stream instanceof ByteArrayInputStream) {
            try {
                stream.reset();
            } catch (IOException ioe) {
                LOG.error("Unable to reset the stream ", ioe);
            }
        }
    }
}
