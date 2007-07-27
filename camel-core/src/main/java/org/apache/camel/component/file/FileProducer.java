/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.camel.component.file;

import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * A {@link Producer} implementation for File
 *
 * @version $Revision: 523016 $
 */
public class FileProducer extends DefaultProducer {
    private static final transient Log log = LogFactory.getLog(FileProducer.class);
    private final FileEndpoint endpoint;

    public FileProducer(FileEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    /**
     * @param exchange
     * @see org.apache.camel.Processor#process(Exchange)
     */
    public void process(Exchange exchange) throws Exception {
        process(endpoint.toExchangeType(exchange));
    }

    public void process(FileExchange exchange) throws Exception {
        ByteBuffer payload = exchange.getIn().getBody(ByteBuffer.class);
        if (payload == null) {
            InputStream in = ExchangeHelper.getMandatoryInBody(exchange, InputStream.class);
            payload = ExchangeHelper.convertToMandatoryType(exchange, ByteBuffer.class, in);
        }
        payload.flip();
        File file = createFileName(exchange);
        buildDirectory(file);
        if (log.isDebugEnabled()) {
            log.debug("Creating file: " + file);
        }
        try {
            FileChannel fc = new RandomAccessFile(file, "rw").getChannel();
            fc.position(fc.size());
            fc.write(payload);
            fc.close();
        }
        catch (Throwable e) {
            log.error("Failed to write to File: " + file, e);
        }
    }

    protected File createFileName(FileExchange exchange) {
        String fileName = exchange.getIn().getMessageId();

        File endpointFile = endpoint.getFile();
        String name = exchange.getIn().getHeader(FileComponent.HEADER_FILE_NAME, String.class);
        if (name != null) {
            File answer = new File(endpointFile, name);
            if (answer.isDirectory()) {
                return new File(answer, fileName);
            }
            else {
                return answer;
            }
        }
        if (endpointFile != null && endpointFile.isDirectory()) {
            return new File(endpointFile, fileName);
        }
        else {
            return new File(fileName);
        }
    }

    private void buildDirectory(File file) {
        String dirName = file.getAbsolutePath();
        int index = dirName.lastIndexOf(File.separatorChar);
        if (index > 0) {
            dirName = dirName.substring(0, index);
            File dir = new File(dirName);
            dir.mkdirs();
        }
    }
}
