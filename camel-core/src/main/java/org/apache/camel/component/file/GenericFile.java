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
import java.util.Date;

import org.apache.camel.util.ObjectHelper;

/**
 * Generic File. Specific implementations of a file based endpoint need to
 * provide a File for transfer.
 */
public class GenericFile<T> {

    private String absoluteFileName;
    private String canonicalFileName;
    private String relativeFileName;
    private String fileName;
    private long fileLength;
    private long lastModified;
    private T file;
    private GenericFileBinding<T> binding;

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

    /**
     * Changes the name of this remote file. This method alters the absolute and
     * relative names as well.
     *
     * @param newName the new name
     */
    public void changeFileName(String newName) {
        if (System.getProperty("os.name").startsWith("Windows") && newName.indexOf("/") >= 0) {
            newName = newName.replaceAll("/", "\\\\");
        }
        setAbsoluteFileName(getParent() + File.separator + newName);
        
        // relative name is a bit more complex to set as newName itself can contain
        // folders we need to consider as well
        String baseNewName = null;
        if (newName.indexOf(File.separator) != -1) {
            baseNewName = newName.substring(0, newName.lastIndexOf(File.separator));
            newName = newName.substring(newName.lastIndexOf(File.separator) + 1);
        }

        if (relativeFileName.indexOf(File.separator) != -1) {
            String relative = relativeFileName.substring(0, relativeFileName.lastIndexOf(File.separator));
            if (baseNewName != null) {
                setRelativeFileName(relative + File.separator + baseNewName + File.separator + newName);
            } else {
                setRelativeFileName(relative + File.separator + newName);
            }
        } else {
            if (baseNewName != null) {
                setRelativeFileName(baseNewName + File.separator + newName);
            } else {
                setRelativeFileName(newName);
            }
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
        return getBinding().getBody(this);
    }

    public void setBody(Object os) {
        getBinding().setBody(this, os);
    }

    public String getParent() {       
        if (getAbsoluteFileName().lastIndexOf(File.separator) > 0) {
            return getAbsoluteFileName().substring(0, getAbsoluteFileName().lastIndexOf(File.separator));
        } else {
            return "";
        }
    }

    public GenericFileBinding<T> getBinding() {
        if (binding == null) {
            binding = new GenericFileDefaultBinding();
        }
        return binding;
    }

    public void setBinding(GenericFileBinding<T> binding) {
        this.binding = binding;
    }

    /**
     * @param absoluteFileName the absoluteFileName to set
     */
    public void setAbsoluteFileName(String absoluteFileName) {
        // should replace the "/" with "\\" in windows
        if (absoluteFileName != null && System.getProperty("os.name").startsWith("Windows") && absoluteFileName.indexOf("/") >= 0) {
            this.absoluteFileName = absoluteFileName.replaceAll("/", "\\\\");
        } else {
            this.absoluteFileName = absoluteFileName;
        }
    }

    /**
     * @return the absoluteFileName
     */
    public String getAbsoluteFileName() {
        return absoluteFileName;
    }

    public String getCanonicalFileName() {
        return canonicalFileName;
    }

    public void setCanonicalFileName(String canonicalFileName) {
        this.canonicalFileName = canonicalFileName;
    }

    @Override
    public String toString() {
        return "GenericFile[" + fileName + "]";
    }
}
