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
package org.apache.camel.converter.stream;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.StreamCache;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;

public class CachedOutputStream extends OutputStream {
    public static final String THRESHOLD = "CamelCachedOutputStreamThreshold";
    public static final String TEMP_DIR = "CamelCachedOutputStreamOutputDirectory";
   
    protected boolean outputLocked;
    protected OutputStream currentStream;

    private long threshold = 64 * 1024;

    private int totalLength;

    private boolean inmem;

    private File tempFile;

    private File outputDir;   
    
    private List<Object> streamList = new ArrayList<Object>();

    
    public CachedOutputStream() {
        currentStream = new ByteArrayOutputStream(2048);
        inmem = true;
    }

    public CachedOutputStream(long threshold) {
        this();
        this.threshold = threshold;        
    }
    
    public CachedOutputStream(Map<String, String> properties) {
        this();
        String value = properties.get(THRESHOLD);
        if (value != null) {
            int i = Integer.parseInt(value);
            if (i > 0) {
                threshold = i;
            }
        }
        value = properties.get(TEMP_DIR);
        if (value != null) {
            File f = new File(value);
            if (f.exists() && f.isDirectory()) {
                outputDir = f;
            } else {
                outputDir = null;
            }
        } else {
            outputDir = null;
        }        
    }

    /**
     * Perform any actions required on stream flush (freeze headers, reset
     * output stream ... etc.)
     */
    protected void doFlush() throws IOException {
        
    }

    public void flush() throws IOException {
        currentStream.flush();       
        doFlush();
    }

    /**
     * Perform any actions required on stream closure (handle response etc.)
     */
    protected void doClose() throws IOException {
        
    }
    
    /**
     * Perform any actions required after stream closure (close the other related stream etc.)
     */
    protected void postClose() throws IOException {
        
    }

    /**
     * Locks the output stream to prevent additional writes, but maintains
     * a pointer to it so an InputStream can be obtained
     * @throws IOException
     */
    public void lockOutputStream() throws IOException {
        currentStream.flush();
        outputLocked = true;
        streamList.remove(currentStream);
    }
    
    public void close() throws IOException {
        currentStream.flush();        
        doClose();
        currentStream.close();
        maybeDeleteTempFile(currentStream);
        postClose();
    }

    public boolean equals(Object obj) {
        return currentStream.equals(obj);
    }

    /**
     * Replace the original stream with the new one, optionally copying the content of the old one
     * into the new one.
     * When with Attachment, needs to replace the xml writer stream with the stream used by
     * AttachmentSerializer or copy the cached output stream to the "real"
     * output stream, i.e. onto the wire.
     * 
     * @param out the new output stream
     * @param copyOldContent flag indicating if the old content should be copied
     * @throws IOException
     */
    public void resetOut(OutputStream out, boolean copyOldContent) throws IOException {
        if (out == null) {
            out = new ByteArrayOutputStream();
        }

        if (currentStream instanceof CachedOutputStream) {
            CachedOutputStream ac = (CachedOutputStream) currentStream;
            InputStream in = ac.getInputStream();
            IOHelper.copyAndCloseInput(in, out);
        } else {
            if (inmem) {
                if (currentStream instanceof ByteArrayOutputStream) {
                    ByteArrayOutputStream byteOut = (ByteArrayOutputStream) currentStream;
                    if (copyOldContent && byteOut.size() > 0) {
                        byteOut.writeTo(out);
                    }
                } else {
                    throw new IOException("Unknown format of currentStream");
                }
            } else {
                // read the file
                currentStream.close();
                FileInputStream fin = new FileInputStream(tempFile);
                if (copyOldContent) {
                    IOHelper.copyAndCloseInput(fin, out);
                }
                streamList.remove(currentStream);
                tempFile.delete();
                tempFile = null;
                inmem = true;
            }
        }
        currentStream = out;
        outputLocked = false;
    }

    public static void copyStream(InputStream in, OutputStream out, int bufferSize) throws IOException {
        IOHelper.copyAndCloseInput(in, out, bufferSize);
    }

    public int size() {
        return totalLength;
    }
    
    public byte[] getBytes() throws IOException {
        flush();
        if (inmem) {
            if (currentStream instanceof ByteArrayOutputStream) {
                return ((ByteArrayOutputStream)currentStream).toByteArray();
            } else {
                throw new IOException("Unknown format of currentStream");
            }
        } else {
            // read the file
            FileInputStream fin = new FileInputStream(tempFile);
            return IOConverter.toBytes(fin);
        }
    }
    
    public void writeCacheTo(OutputStream out) throws IOException {
        flush();
        if (inmem) {
            if (currentStream instanceof ByteArrayOutputStream) {
                ((ByteArrayOutputStream)currentStream).writeTo(out);
            } else {
                throw new IOException("Unknown format of currentStream");
            }
        } else {
            // read the file
            FileInputStream fin = new FileInputStream(tempFile);
            IOHelper.copyAndCloseInput(fin, out);
        }
    }
    
    
    public void writeCacheTo(StringBuilder out, int limit) throws IOException {
        flush();
        if (totalLength < limit
            || limit == -1) {
            writeCacheTo(out);
            return;
        }
        
        int count = 0;
        if (inmem) {
            if (currentStream instanceof ByteArrayOutputStream) {
                byte bytes[] = ((ByteArrayOutputStream)currentStream).toByteArray();
                out.append(IOHelper.newStringFromBytes(bytes, 0, limit));
            } else {
                throw new IOException("Unknown format of currentStream");
            }
        } else {
            // read the file
            FileInputStream fin = new FileInputStream(tempFile);
            byte bytes[] = new byte[1024];
            int x = fin.read(bytes);
            while (x != -1) {
                if ((count + x) > limit) {
                    x = limit - count;
                }
                out.append(IOHelper.newStringFromBytes(bytes, 0, x));
                count += x;
                
                if (count >= limit) {
                    x = -1;
                } else {
                    x = fin.read(bytes);
                }
            }
            fin.close();
        }
    }
    public void writeCacheTo(StringBuilder out) throws IOException {
        flush();
        if (inmem) {
            if (currentStream instanceof ByteArrayOutputStream) {
                byte[] bytes = ((ByteArrayOutputStream)currentStream).toByteArray();
                out.append(IOHelper.newStringFromBytes(bytes));
            } else {
                throw new IOException("Unknown format of currentStream");
            }
        } else {
            // read the file
            FileInputStream fin = new FileInputStream(tempFile);
            byte bytes[] = new byte[1024];
            int x = fin.read(bytes);
            while (x != -1) {
                out.append(IOHelper.newStringFromBytes(bytes, 0, x));
                x = fin.read(bytes);
            }
            fin.close();
        }
    }    
    

    /**
     * @return the underlying output stream
     */
    public OutputStream getOut() {
        return currentStream;
    }

    public int hashCode() {
        return currentStream.hashCode();
    }

    public String toString() {
        StringBuilder builder = new StringBuilder().append("[")
            .append(CachedOutputStream.class.getName())
            .append(" Content: ");
        try {
            writeCacheTo(builder);
        } catch (IOException e) {
            //ignore
        }
        return builder.append("]").toString();
    }

    protected void onWrite() throws IOException {
        
    }

    public void write(byte[] b, int off, int len) throws IOException {
        if (!outputLocked) {
            onWrite();
            this.totalLength += len;
            if (inmem && totalLength > threshold && currentStream instanceof ByteArrayOutputStream) {
                createFileOutputStream();
            }
            currentStream.write(b, off, len);
        }
    }

    public void write(byte[] b) throws IOException {
        if (!outputLocked) {
            onWrite();
            this.totalLength += b.length;
            if (inmem && totalLength > threshold && currentStream instanceof ByteArrayOutputStream) {
                createFileOutputStream();
            }
            currentStream.write(b);
        }
    }

    public void write(int b) throws IOException {
        if (!outputLocked) {
            onWrite();
            this.totalLength++;
            if (inmem && totalLength > threshold && currentStream instanceof ByteArrayOutputStream) {
                createFileOutputStream();
            }
            currentStream.write(b);
        }
    }

    private void createFileOutputStream() throws IOException {
        ByteArrayOutputStream bout = (ByteArrayOutputStream)currentStream;
        if (outputDir == null) {
            tempFile = FileUtil.createTempFile("cos", "tmp");
        } else {
            tempFile = FileUtil.createTempFile("cos", "tmp", outputDir, false);
        }
        
        currentStream = new BufferedOutputStream(new FileOutputStream(tempFile));
        bout.writeTo(currentStream);
        inmem = false;
        streamList.add(currentStream);
    }

    public File getTempFile() {
        return tempFile != null && tempFile.exists() ? tempFile : null;
    }

    public InputStream getInputStream() throws IOException {
        flush();
        if (inmem) {
            if (currentStream instanceof ByteArrayOutputStream) {
                return new ByteArrayInputStream(((ByteArrayOutputStream) currentStream).toByteArray());
            } else {
                return null;
            }
        } else {
            try {
                FileInputStream fileInputStream = new FileInputStream(tempFile) {
                    public void close() throws IOException {
                        super.close();
                        maybeDeleteTempFile(this);
                    }
                };
                streamList.add(fileInputStream);
                return fileInputStream;
            } catch (FileNotFoundException e) {
                throw new IOException("Cached file was deleted, " + e.toString());
            }
        }
    }
    
    public StreamCache getStreamCache() throws IOException {
        flush();
        if (inmem) {
            if (currentStream instanceof ByteArrayOutputStream) {
                return new InputStreamCache(((ByteArrayOutputStream) currentStream).toByteArray());
            } else {
                return null;
            }
        } else {
            try {
                FileInputStreamCache fileInputStream = new FileInputStreamCache(tempFile, this);
                return fileInputStream;
            } catch (FileNotFoundException e) {
                throw new IOException("Cached file was deleted, " + e.toString());
            }
        }
    }
    
    private void maybeDeleteTempFile(Object stream) {        
        streamList.remove(stream);        
        if (!inmem && tempFile != null && streamList.isEmpty()) {            
            tempFile.delete();
            tempFile = null;
            currentStream = new ByteArrayOutputStream(1024);
            inmem = true;
        }
    }

    public void setOutputDir(File outputDir) throws IOException {
        this.outputDir = outputDir;
    }
    public void setThreshold(long threshold) {
        this.threshold = threshold;
    }

}
