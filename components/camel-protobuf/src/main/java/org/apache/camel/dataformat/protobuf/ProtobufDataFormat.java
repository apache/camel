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
package org.apache.camel.dataformat.protobuf;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

public class ProtobufDataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {

    private CamelContext camelContext;
    private Message defaultInstance;
    private String instanceClassName;
    
    public ProtobufDataFormat() {
    }

    public ProtobufDataFormat(Message defaultInstance) {
        this.defaultInstance = defaultInstance;
    }

    @Override
    public String getDataFormatName() {
        return "protobuf";
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public void setDefaultInstance(Message instance) {
        this.defaultInstance = instance;
    }
    
    public void setDefaultInstance(Object instance) {
        if (instance instanceof Message) {
            this.defaultInstance = (Message) instance;
        } else {
            throw new IllegalArgumentException("The argument for setDefaultInstance should be subClass of com.google.protobuf.Message");
        }
    }
    
    public void setInstanceClass(String className) throws Exception {
        ObjectHelper.notNull(className, "ProtobufDataFormat instaceClass");
        instanceClassName = className;
    }
    
    /*
     * (non-Javadoc)
     * @see org.apache.camel.spi.DataFormat#marshal(org.apache.camel.Exchange,
     * java.lang.Object, java.io.OutputStream)
     */
    public void marshal(Exchange exchange, Object graph, OutputStream outputStream) throws Exception {
        ((Message)graph).writeTo(outputStream);
    }

    /*
     * (non-Javadoc)
     * @see org.apache.camel.spi.DataFormat#unmarshal(org.apache.camel.Exchange,
     * java.io.InputStream)
     */
    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        ObjectHelper.notNull(defaultInstance, "defaultInstance or instanceClassName must be set", this);

        Builder builder = defaultInstance.newBuilderForType().mergeFrom(inputStream);
        if (!builder.isInitialized()) {
            // TODO which exception should be thrown here?
            throw new InvalidPayloadException(exchange, defaultInstance.getClass());
        }

        return builder.build();
    }

    protected Message loadDefaultInstance(String className, CamelContext context) throws CamelException, ClassNotFoundException {
        Class<?> instanceClass = context.getClassResolver().resolveMandatoryClass(className);
        if (Message.class.isAssignableFrom(instanceClass)) {
            try {
                Method method = instanceClass.getMethod("getDefaultInstance");
                return (Message) method.invoke(null, new Object[0]);
            } catch (Exception ex) {
                throw new CamelException("Can't set the defaultInstance of ProtobufferDataFormat with "
                        + className + ", caused by " + ex);
            }
        } else {
            throw new CamelException("Can't set the defaultInstance of ProtobufferDataFormat with "
                    + className + ", as the class is not a subClass of com.google.protobuf.Message");
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (defaultInstance == null && instanceClassName != null) {
            defaultInstance = loadDefaultInstance(instanceClassName, getCamelContext());
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

}
