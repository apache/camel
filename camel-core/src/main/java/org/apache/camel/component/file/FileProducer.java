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
package org.apache.camel.component.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.UuidGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * For producing files.
 *
 * @version $Revision$
 */
public class FileProducer extends DefaultProducer {
    private static final transient Log LOG = LogFactory.getLog(FileProducer.class);
    private FileEndpoint endpoint;

    public FileProducer(FileEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public FileEndpoint getEndpoint() {
        return (FileEndpoint) super.getEndpoint();
    }

    public void process(Exchange exchange) throws Exception {
        FileExchange fileExchange = endpoint.createExchange(exchange);
        process(fileExchange);
        ExchangeHelper.copyResults(exchange, fileExchange);
    }

    public void process(FileExchange exchange) throws Exception {
        InputStream in = ExchangeHelper.getMandatoryInBody(exchange, InputStream.class);
        File file = createFileName(exchange.getIn());
        buildDirectory(file);

        if (LOG.isDebugEnabled()) {
            LOG.debug("About to write to: " + file + " from exchange: " + exchange);
        }
        FileChannel fc = null;
        try {
            if (getEndpoint().isAppend()) {
                fc = new RandomAccessFile(file, "rw").getChannel();
                fc.position(fc.size());
            } else {
                fc = new FileOutputStream(file).getChannel();
            }

            int size = getEndpoint().getBufferSize();
            byte[] buffer = new byte[size];
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            while (true) {
                int count = in.read(buffer);
                if (count <= 0) {
                    break;
                } else if (count < size) {
                    byteBuffer = ByteBuffer.wrap(buffer, 0, count);
                    fc.write(byteBuffer);
                    break;
                } else {
                    fc.write(byteBuffer);
                    byteBuffer.clear();
                }
            }
        } finally {
            ObjectHelper.close(in, file.getName(), LOG);
            ObjectHelper.close(fc, file.getName(), LOG);
        }
    }

    protected File createFileName(Message message) {
        File answer;

        String name = null;
        if (!endpoint.isIgnoreFileNameHeader()) {
            name = message.getHeader(FileComponent.HEADER_FILE_NAME, String.class);
        }

        File endpointFile = endpoint.getFile();
        if (endpointFile.isDirectory()) {
            if (name != null) {
                answer = new File(endpointFile, name);
                if (answer.isDirectory()) {
                    answer = new File(answer, endpoint.getGeneratedFileName(message));
                }
            } else {
                answer = new File(endpointFile, endpoint.getGeneratedFileName(message));
            }
        } else {
            if (name == null) {
                answer = endpointFile;
            } else {
                answer = new File(endpointFile, name);
            }
        }

        // lets store the name we really used in the header, so end-users can retrieve it
        message.setHeader(FileComponent.HEADER_FILE_NAME_PRODUCED, answer.getAbsolutePath());

        return answer;
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
