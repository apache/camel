/**
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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import org.w3c.dom.Document;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Default converters for {@link ExecResult}. For details how to extend the
 * converters check out <a
 * href="http://camel.apache.org/type-converter.html">the Camel docs for type
 * converters.</a>
 */
@Converter
public final class ExecResultConverter {

    private static final Log LOG = LogFactory.getLog(ExecResultConverter.class);

    private ExecResultConverter() {
    }

    @Converter
    public static InputStream convertToInputStream(ExecResult result) throws FileNotFoundException {
        return toInputStream(result);
    }

    @Converter
    public static String convertToString(ExecResult result, Exchange exchange) throws FileNotFoundException {
        return convertTo(String.class, exchange, result);
    }

    @Converter
    public static Document convertToDocument(ExecResult result, Exchange exchange) throws FileNotFoundException {
        return convertTo(Document.class, exchange, result);
    }

    @Converter
    public static byte[] convertToByteArray(ExecResult result, Exchange exchange) throws FileNotFoundException {
        return convertTo(byte[].class, exchange, result);
    }

    /**
     * Converts <code>ExecResult</code> to the type <code>T</code>.
     * 
     * @param <T> The type to convert to
     * @param type Class instance of the type to which to convert
     * @param exchange a Camel exchange. If exchange is <code>null</code>, no
     *            conversion will be made
     * @param result
     * @return the converted {@link ExecResult}
     * @throws FileNotFoundException if theres is a file in the execResult, and
     *             the file can not be found
     */
    public static <T> T convertTo(Class<T> type, Exchange exchange, ExecResult result) throws FileNotFoundException {
        if (exchange != null) {
            return (T)exchange.getContext().getTypeConverter().convertTo(type, exchange, toInputStream(result));
        } else {
            // should revert to fallback converter if we don't have an exchange
            return null;
        }
    }

    /**
     * If the ExecResult contains out file,
     * <code>InputStream<code> with the output of the <code>execResult</code>.
     * If there is {@link ExecCommand#getOutFile()}, its content is preferred to
     * {@link ExecResult#getStdout()}
     * 
     * @param execResult ExecResult object.
     * @return InputStream object
     * @throws FileNotFoundException if the {@link ExecResult#getOutFile()} is
     *             not <code>null</code>, but can not be found
     */
    public static InputStream toInputStream(ExecResult execResult) throws FileNotFoundException {
        if (execResult == null) {
            LOG.error("Unable to convert a null exec result!");
            return null;
        }
        InputStream resultVal = execResult.getStdout();
        // prefer generic file conversion
        if (execResult.getCommand().getOutFile() != null) {
            resultVal = new FileInputStream(execResult.getCommand().getOutFile());
        }
        return resultVal;
    }
}
