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
package org.apache.camel.component.file.remote;

import java.io.OutputStream;

/**
 * Represents a remote file of some sort of backing object
 */
public class RemoteFile<T> implements Cloneable {

    private String absolutelFileName;
    private String relativeFileName;
    private String fileName;
    private String hostname;
    private long fileLength;
    private long lastModified;
    private T file;
    private OutputStream body;

    @Override
    public RemoteFile<T> clone() {
        return copyFrom(this);
    }

    /**
     * Creates a clone based on the source
     *
     * @param source  the source
     * @return a clone of the source
     */
    public RemoteFile<T> copyFrom(RemoteFile<T> source) {
        RemoteFile<T> result = new RemoteFile<T>();
        result.setAbsolutelFileName(source.getAbsolutelFileName());
        result.setRelativeFileName(source.getRelativeFileName());
        result.setFileName(source.getFileName());
        result.setHostname(source.getHostname());
        result.setFileLength(source.getFileLength());
        result.setLastModified(source.getLastModified());
        result.setFile(source.getFile());
        result.setBody(source.getBody());
        return result;
    }

    /**
     * Changes the name of this remote file. This method alters the absolute and relative names as well.
     *
     * @param newName the new name
     */
    public void changeFileName(String newName) {
        setAbsolutelFileName(getParent() + "/" + newName);
        if (relativeFileName.indexOf("/") != -1) {
            setRelativeFileName(relativeFileName.substring(0, relativeFileName.lastIndexOf("/")) + newName);
        } else {
            setRelativeFileName(newName);
        }
        setFileName(newName);
    }

    public String getAbsolutelFileName() {
        return absolutelFileName;
    }

    public void setAbsolutelFileName(String absolutelFileName) {
        this.absolutelFileName = absolutelFileName;
    }

    public String getRelativeFileName() {
        return relativeFileName;
    }

    public void setRelativeFileName(String relativeFileName) {
        this.relativeFileName = relativeFileName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public long getFileLength() {
        return fileLength;
    }

    public void setFileLength(long fileLength) {
        this.fileLength = fileLength;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public T getFile() {
        return file;
    }

    public void setFile(T file) {
        this.file = file;
    }

    public OutputStream getBody() {
        return body;
    }

    public void setBody(OutputStream os) {
        this.body = os;
    }

    public String getParent() {
        return absolutelFileName.substring(0, absolutelFileName.lastIndexOf("/"));
    }

    @Override
    public String toString() {
        return absolutelFileName;
    }
}
