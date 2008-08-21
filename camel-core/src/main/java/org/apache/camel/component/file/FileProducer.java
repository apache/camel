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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.language.simple.FileLanguage;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
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

    /**
     * @deprecated will be removed in Camel 2.0.
     */
    public FileEndpoint getEndpoint() {
        return endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        FileExchange fileExchange = endpoint.createExchange(exchange);
        process(fileExchange);
        ExchangeHelper.copyResults(exchange, fileExchange);
    }

    public void process(FileExchange exchange) throws Exception {
        boolean fileSource = exchange.getIn().getBody() instanceof File;
        File target = createFileName(exchange.getIn());
        buildDirectory(target);

        if (LOG.isDebugEnabled()) {
            LOG.debug("About to write to: " + target + " from exchange: " + exchange);
        }

        if (fileSource) {
            File source = ExchangeHelper.getMandatoryInBody(exchange, File.class);
            writeFileByFile(source, target);
        } else {
            InputStream in = ExchangeHelper.getMandatoryInBody(exchange, InputStream.class);
            writeFileByStream(in, target);
        }
    }

    private void writeFileByFile(File source, File target) throws IOException {
        FileChannel in = new FileInputStream(source).getChannel();
        FileChannel out = null;
        try {
            out = prepareOutputFileChannel(target, out);

            if (LOG.isTraceEnabled()) {
                LOG.trace("Using FileChannel to transfer from: " + in + " to: " + out);
            }
            in.transferTo(0, in.size(), out);
        } finally {
            ObjectHelper.close(in, source.getName(), LOG);
            ObjectHelper.close(out, source.getName(), LOG);
        }
    }

    private void writeFileByStream(InputStream in, File target) throws IOException {
        FileChannel out = null;
        try {
            out = prepareOutputFileChannel(target, out);

            if (LOG.isTraceEnabled()) {
                LOG.trace("Using InputStream to transfer from: " + in + " to: " + out);
            }
            int size = endpoint.getBufferSize();
            byte[] buffer = new byte[size];
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            while (true) {
                int count = in.read(buffer);
                if (count <= 0) {
                    break;
                } else if (count < size) {
                    byteBuffer = ByteBuffer.wrap(buffer, 0, count);
                    out.write(byteBuffer);
                    break;
                } else {
                    out.write(byteBuffer);
                    byteBuffer.clear();
                }
            }
        } finally {
            ObjectHelper.close(in, target.getName(), LOG);
            ObjectHelper.close(out, target.getName(), LOG);
        }
    }

    /**
     * Creates and prepares the output file channel. Will position itself in correct position if eg. it should append
     * or override any existing content.
     */
    private FileChannel prepareOutputFileChannel(File target, FileChannel out) throws IOException {
        if (endpoint.isAppend()) {
            out = new RandomAccessFile(target, "rw").getChannel();
            out = out.position(out.size());
        } else {
            out = new FileOutputStream(target).getChannel();
        }
        return out;
    }

    protected File createFileName(Message message) {
        File answer;

        String name = null;
        if (!endpoint.isIgnoreFileNameHeader()) {
            name = message.getHeader(FileComponent.HEADER_FILE_NAME, String.class);
        }

        // expression support
        Expression expression = endpoint.getExpression();
        if (name != null) {
            // the header name can be an expression too, that should override whatever configured on the endpoint
            if (name.indexOf("${") > -1) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(FileComponent.HEADER_FILE_NAME + " contains a FileLanguage expression: " + name);
                }
                expression = FileLanguage.file(name);
            }
        }
        if (expression != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Filename evaluated as expression: " + expression);
            }
            Object result = expression.evaluate(message.getExchange());
            name = message.getExchange().getContext().getTypeConverter().convertTo(String.class, result);
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
