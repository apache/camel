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
package org.apache.camel.component.file;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultMessage;

/**
 * Generic file message
 */
public class GenericFileMessage<T> extends DefaultMessage {
    private GenericFile<T> file;

    public GenericFileMessage(CamelContext camelContext) {
        super(camelContext);
    }

    public GenericFileMessage(Exchange exchange, GenericFile<T> file) {
        super(exchange);
        this.file = file;
    }

    public GenericFileMessage(CamelContext camelContext, GenericFile<T> file) {
        super(camelContext);
        this.file = file;
    }

    @Override
    protected Object createBody() {
        return file != null ? file.getBody() : super.createBody();
    }

    public GenericFile<T> getGenericFile() {
        return file;
    }

    public void setGenericFile(GenericFile<T> file) {
        this.file = file;
    }

    @Override
    public GenericFileMessage<T> newInstance() {
        return new GenericFileMessage<>(getCamelContext());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void copyFrom(Message that) {
        super.copyFrom(that);

        if (that instanceof GenericFileMessage) {
            setGenericFile(((GenericFileMessage)that).getGenericFile());
        }
    }

    @Override
    public String toString() {
        // only output the filename as body can be big
        if (file != null) {
            return file.getFileName();
        }
        return getClass().getName() + "@" + Integer.toHexString(hashCode());
    }
}
