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

import org.apache.camel.util.ObjectHelper;

/**
 * Generic File. Specific implementations of a file based endpoint need to
 * provide a File for transfer.
 */
public class GenericFile<T> {

    private String absoluteFileName;
    private String relativeFileName;
    private String fileName;
    private long fileLength;
    private long lastModified;
    private T file;
    private Object body;

    @Override
    public GenericFile<T> clone() {
        return copyFrom(this);
    }

    /**
     * Creates a clone based on the source
     *
     * @param source the source
     * @return a clone of the source
     */
    public GenericFile<T> copyFrom(GenericFile<T> source) {
        GenericFile<T> result;
        try {
            result = source.getClass().newInstance();
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        result.setAbsoluteFileName(source.getAbsoluteFileName());
        result.setRelativeFileName(source.getRelativeFileName());
        result.setFileName(source.getFileName());
        result.setFileLength(source.getFileLength());
        result.setLastModified(source.getLastModified());
        result.setFile(source.getFile());
        result.setBody(source.getBody());
        return result;
    }

    /**
     * Changes the name of this remote file. This method alters the absolute and
     * relative names as well.
     *
     * @param newName the new name
     */
    public void changeFileName(String newName) {
        setAbsoluteFileName(getParent() + "/" + newName);
        if (relativeFileName.indexOf("/") != -1) {
            String relative = relativeFileName.substring(0, relativeFileName.lastIndexOf("/"));
            setRelativeFileName(relative + newName);
        } else {
            setRelativeFileName(newName);
        }
        setFileName(newName);
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

    public Object getBody() {
        return body;
    }

    public void setBody(Object os) {
        this.body = os;
    }

    public String getParent() {
        return getAbsoluteFileName().substring(0, getAbsoluteFileName().lastIndexOf("/"));
    }

    @Override
    public String toString() {
        return getAbsoluteFileName();
    }

    /**
     * @param absoluteFileName the absoluteFileName to set
     */
    public void setAbsoluteFileName(String absoluteFileName) {
        this.absoluteFileName = absoluteFileName;
    }

    /**
     * @return the absoluteFileName
     */
    public String getAbsoluteFileName() {
        return absoluteFileName;
    }

}
