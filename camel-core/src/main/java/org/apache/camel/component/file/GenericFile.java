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
import java.io.Serializable;

import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;

/**
 * Generic File. Specific implementations of a file based endpoint need to
 * provide a File for transfer.
 */
public class GenericFile<T> implements Serializable {

    private String endpointPath;
    private String fileName;
    private String fileNameOnly;
    private String relativeFilePath;
    private String absoluteFilePath;
    private long fileLength;
    private long lastModified;
    private T file;
    private GenericFileBinding<T> binding;
    private boolean absolute;

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
    @SuppressWarnings("unchecked")
    public GenericFile<T> copyFrom(GenericFile<T> source) {
        GenericFile<T> result;
        try {
            result = source.getClass().newInstance();
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        result.setEndpointPath(source.getEndpointPath());
        result.setAbsolute(source.isAbsolute());
        result.setAbsoluteFilePath(source.getAbsoluteFilePath());
        result.setRelativeFilePath(source.getRelativeFilePath());
        result.setFileName(source.getFileName());
        result.setFileNameOnly(source.getFileNameOnly());
        result.setFileLength(source.getFileLength());
        result.setLastModified(source.getLastModified());
        result.setFile(source.getFile());
        result.setBody(source.getBody());
        result.setBinding(source.getBinding());
        return result;
    }

    public boolean needToNormalize() {
        return true;
    }

    public String getFileSeparator() {
        return File.separator;
    }

    /**
     * Changes the name of this remote file. This method alters the absolute and
     * relative names as well.
     *
     * @param newName the new name
     */
    public void changeFileName(String newName) {
        newName = needToNormalize() ? FileUtil.normalizePath(newName) : newName;
        boolean absolute = isAbsolutePath(newName);
        boolean nameChangeOnly = newName.indexOf(getFileSeparator()) == -1;

        // use java.io.File to help us with computing name changes
        File file = new File(newName);

        // store the file name only
        setFileNameOnly(file.getName());
        setFileName(file.getName());

        // relative name is a bit more complex
        if (nameChangeOnly) {
            setRelativeFilePath(changeNameOnly(getRelativeFilePath(), file.getName()));
            setFileName(changeNameOnly(getFileName(), file.getName()));
        } else {
            if (file.getParent() != null) {
                setRelativeFilePath(file.getParent() + getFileSeparator() + file.getName());
            } else {
                setRelativeFilePath(file.getName());
            }
        }

        // absolute vs relative
        if (absolute) {
            setAbsolute(true);
            setAbsoluteFilePath(file.getAbsolutePath());
        } else {
            setAbsolute(false);
            // construct a pseudo absolute filename that the file operations uses
            setAbsoluteFilePath(endpointPath + getFileSeparator() + getRelativeFilePath());
        }
    }

    private String changeNameOnly(String path, String name) {
        int pos = path.lastIndexOf(getFileSeparator());
        if (pos != -1) {
            return path.substring(0, pos + 1) + name;
        } else {
            return name;
        }
    }

    private boolean isAbsolutePath(String path) {
        return new File(path).isAbsolute();
    }

    public String getRelativeFilePath() {
        return relativeFilePath;
    }

    public void setRelativeFilePath(String relativeFilePath) {
        this.relativeFilePath = needToNormalize() ? FileUtil.normalizePath(relativeFilePath) : relativeFilePath;
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
        return getBinding().getBody(this);
    }

    public void setBody(Object os) {
        getBinding().setBody(this, os);
    }

    public String getParent() {
        if (isAbsolute()) {
            String name = getAbsoluteFilePath();
            File path = new File(name);
            return path.getParent();
        } else {
            String name = getRelativeFilePath();
            File path = new File(endpointPath, name);
            return path.getParent();
        }
    }

    public GenericFileBinding<T> getBinding() {
        if (binding == null) {
            binding = new GenericFileDefaultBinding<T>();
        }
        return binding;
    }

    public void setBinding(GenericFileBinding<T> binding) {
        this.binding = binding;
    }

    public void setAbsoluteFilePath(String absoluteFilePath) {
        this.absoluteFilePath = needToNormalize() ? FileUtil.normalizePath(absoluteFilePath) : absoluteFilePath;
    }

    public String getAbsoluteFilePath() {
        return absoluteFilePath;
    }

    public boolean isAbsolute() {
        return absolute;
    }

    public void setAbsolute(boolean absolute) {
        this.absolute = absolute;
    }

    public String getEndpointPath() {
        return endpointPath;
    }

    public void setEndpointPath(String endpointPath) {
        this.endpointPath = needToNormalize() ? FileUtil.normalizePath(endpointPath) : endpointPath;
    }

    public String getFileNameOnly() {
        return fileNameOnly;
    }

    public void setFileNameOnly(String fileNameOnly) {
        this.fileNameOnly = fileNameOnly;
    }

    @Override
    public String toString() {
        return "GenericFile[" + (absolute ? absoluteFilePath : relativeFilePath) + "]";
    }
}
