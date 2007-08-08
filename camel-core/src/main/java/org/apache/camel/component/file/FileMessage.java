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

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultMessage;

import java.io.File;

/**
 * A {@link Exchange} for File
 * 
 * @version $Revision: 520985 $
 */
public class FileMessage extends DefaultMessage {
    private File file;

    public FileMessage() {
        this(new File("."));
    }

    public FileMessage(File file) {
        this.file = file;
    }

    @Override
    public String toString() {
        return "FileMessage: " + file;
    }

    @Override
    public FileExchange getExchange() {
        return (FileExchange)super.getExchange();
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    @Override
    public FileMessage newInstance() {
        return new FileMessage();
    }

    @Override
    protected Object createBody() {
        return file;
    }
}
