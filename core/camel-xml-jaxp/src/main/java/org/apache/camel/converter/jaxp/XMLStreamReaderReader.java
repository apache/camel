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
package org.apache.camel.converter.jaxp;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.Reader;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/**
 *
 */
class XMLStreamReaderReader extends Reader {
    private static final int BUFFER_SIZE = 4096;
    private final XMLStreamReader reader;
    private XMLStreamWriter writer;
    private final TrimmableCharArrayWriter chunk;
    private final char[] buffer;
    private int bpos;

    XMLStreamReaderReader(XMLStreamReader reader, XMLOutputFactory outfactory) {
        this.reader = reader;
        this.buffer = new char[BUFFER_SIZE];
        this.chunk = new TrimmableCharArrayWriter();
        try {
            this.writer = outfactory.createXMLStreamWriter(chunk);
        } catch (XMLStreamException e) {
            //ignore
        }
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        int tlen = 0;
        while (len > 0) {
            int n = ensureBuffering(len);
            if (n < 0) {
                break;
            }
            int clen = len > n ? n : len;
            System.arraycopy(buffer, 0, cbuf, off, clen);
            System.arraycopy(buffer, clen, buffer, 0, buffer.length - clen);
            bpos -= clen;
            len -= clen;
            off += clen;
            tlen += clen;
        }
        return tlen > 0 ? tlen : -1;
    }

    private int ensureBuffering(int size) throws IOException {
        if (size < bpos) {
            return bpos;
        }
        // refill the buffer as more buffering is requested than the current buffer status
        try {

            // very first event
            if (XMLStreamConstants.START_DOCUMENT == reader.getEventType()) {
                writer.writeStartDocument("utf-8", "1.0");
            }
            if (chunk.size() < buffer.length) {
                while (reader.hasNext()) {
                    int code = reader.next();
                    switch (code) {
                        case XMLStreamConstants.END_DOCUMENT:
                            writer.writeEndDocument();
                            break;
                        case XMLStreamConstants.START_ELEMENT:
                            QName qname = reader.getName();
                            writer.writeStartElement(qname.getPrefix(), qname.getLocalPart(), qname.getNamespaceURI());
                            for (int i = 0; i < reader.getAttributeCount(); i++) {
                                writer.writeAttribute(
                                        reader.getAttributePrefix(i), reader.getAttributeNamespace(i),
                                        reader.getAttributeLocalName(i),
                                        reader.getAttributeValue(i));
                            }
                            for (int i = 0; i < reader.getNamespaceCount(); i++) {
                                writer.writeNamespace(reader.getNamespacePrefix(i), reader.getNamespaceURI(i));
                            }
                            break;
                        case XMLStreamConstants.END_ELEMENT:
                            writer.writeEndElement();
                            break;
                        case XMLStreamConstants.CHARACTERS:
                            writer.writeCharacters(reader.getText());
                            break;
                        case XMLStreamConstants.COMMENT:
                            writer.writeComment(reader.getText());
                            break;
                        case XMLStreamConstants.CDATA:
                            writer.writeCData(reader.getText());
                            break;
                        default:
                            break;
                    }

                    // check if the chunk is full
                    final int csize = buffer.length - bpos;
                    if (chunk.size() > csize) {
                        System.arraycopy(chunk.getCharArray(), 0, buffer, bpos, csize);
                        bpos = buffer.length;
                        chunk.trim(csize, 0);
                        return buffer.length;
                    }
                }
            }
            final int csize = chunk.size() < buffer.length - bpos ? chunk.size() : buffer.length - bpos;
            if (csize > 0) {
                System.arraycopy(chunk.getCharArray(), 0, buffer, bpos, csize);
                bpos += csize;
                chunk.trim(csize, 0);
                return bpos;
            } else {
                return bpos > 0 ? bpos : -1;
            }
        } catch (XMLStreamException e) {
            throw new IOException(e);
        }
    }

    static class TrimmableCharArrayWriter extends CharArrayWriter {
        public void trim(int head, int tail) {
            System.arraycopy(buf, head, buf, 0, count - head - tail);
            count -= head + tail;
        }

        public char[] toCharArray(int len) {
            char[] c = new char[len];
            System.arraycopy(buf, 0, c, 0, len);
            return c;
        }

        char[] getCharArray() {
            return buf;
        }
    }
}
