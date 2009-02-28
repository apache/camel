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

    private String absoluteFileName;
    private String canonicalFileName;
    private String relativeFileName;
    private String fileName;
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
        result.setAbsolute(source.isAbsolute());
        result.setAbsoluteFileName(source.getAbsoluteFileName());
        result.setCanonicalFileName(source.getCanonicalFileName());
        result.setRelativeFileName(source.getRelativeFileName());
        result.setFileName(source.getFileName());
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
        newName = needToNormalize()
                // must normalize path to cater for Windows and other OS
                ? FileUtil.normalizePath(newName)
                // for the remote file we don't need to do that
                : newName;

        // is it relative or absolute
        boolean absolute = isAbsolutePath(newName);

        if (absolute) {
            setAbsolute(true);
            setAbsoluteFileName(newName);
            // no relative filename for absolute files
            setRelativeFileName(null);
            String fileName = newName.substring(newName.lastIndexOf(getFileSeparator()) + 1);
            setFileName(fileName);
            return;
        }

        // the rest is complex relative path computation
        setAbsolute(false);
        setAbsoluteFileName(getParent() + getFileSeparator() + newName);

        // relative name is a bit more complex to set as newName itself can contain
        // folders we need to consider as well
        String baseNewName = null;
        if (newName.indexOf(getFileSeparator()) != -1) {
            baseNewName = newName.substring(0, newName.lastIndexOf(getFileSeparator()));
            newName = newName.substring(newName.lastIndexOf(getFileSeparator()) + 1);
        }

        if (relativeFileName.indexOf(getFileSeparator()) != -1) {
            String relative = relativeFileName.substring(0, relativeFileName.lastIndexOf(File.separator));
            if (baseNewName != null) {
                setRelativeFileName(relative + getFileSeparator() + baseNewName + getFileSeparator() + newName);
            } else {
                setRelativeFileName(relative + getFileSeparator() + newName);
            }
        } else {
            if (baseNewName != null) {
                setRelativeFileName(baseNewName + getFileSeparator() + newName);
            } else {
                setRelativeFileName(newName);
            }
        }

        setFileName(newName);
    }

    private boolean isAbsolutePath(String path) {
        if (file instanceof File) {
            // let java.io.File deal with it as its better than me
            return new File(path).isAbsolute();
        }
        // otherwise absolute is considered if we start with the separator
        return path.startsWith(getFileSeparator());
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
        return getBinding().getBody(this);
    }

    public void setBody(Object os) {
        getBinding().setBody(this, os);
    }

    public String getParent() {
        if (getAbsoluteFileName().lastIndexOf(getFileSeparator()) > 0) {
            return getAbsoluteFileName().substring(0, getAbsoluteFileName().lastIndexOf(getFileSeparator()));
        } else {
            return "";
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

    public void setAbsoluteFileName(String absoluteFileName) {
        this.absoluteFileName = needToNormalize()
                // must normalize path to cater for Windows and other OS
                ? FileUtil.normalizePath(absoluteFileName)
                // we don't need to do that for Remote File
                : absoluteFileName;
    }

    public String getAbsoluteFileName() {
        return absoluteFileName;
    }

    public String getCanonicalFileName() {
        return canonicalFileName;
    }

    public void setCanonicalFileName(String canonicalFileName) {
        this.canonicalFileName = canonicalFileName;
    }

    public boolean isAbsolute() {
        return absolute;
    }

    public void setAbsolute(boolean absolute) {
        this.absolute = absolute;
    }

    @Override
    public String toString() {
        return "GenericFile[" + (absolute ? absoluteFileName : relativeFileName) + "]";
    }
}
