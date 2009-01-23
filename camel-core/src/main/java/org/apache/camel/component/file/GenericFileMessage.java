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

import org.apache.camel.impl.DefaultMessage;

/**
 * Remote file message
 */
public class GenericFileMessage<T> extends DefaultMessage {
    private GenericFile genericFile;

    public GenericFileMessage() {
    }

    public GenericFileMessage(GenericFile genericFile) {
        this.genericFile = genericFile;
    }

    @Override
    public GenericFileExchange getExchange() {
        return (GenericFileExchange) super.getExchange();
    }

    @Override
    protected Object createBody() {
        return genericFile.getBody();
    }

    public GenericFile<T> getGenericFile() {
        return genericFile;
    }

    public void setRemoteFile(GenericFile genericFile) {
        this.genericFile = genericFile;
    }

    @Override
    public GenericFileMessage newInstance() {
        return new GenericFileMessage();
    }

    @Override
    public String toString() {
        return "GenericFileMessage: " + genericFile;
    }
}
