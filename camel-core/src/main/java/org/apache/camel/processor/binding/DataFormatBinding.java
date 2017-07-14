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
package org.apache.camel.processor.binding;

import org.apache.camel.Processor;
import org.apache.camel.processor.MarshalProcessor;
import org.apache.camel.processor.UnmarshalProcessor;
import org.apache.camel.spi.Binding;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents a {@link org.apache.camel.spi.Binding} which Marshals the message in the ProduceProcessor and
 * Unmarshals the message in the ConsumeProcessor
 */
@Deprecated
public class DataFormatBinding extends ServiceSupport implements Binding {
    private DataFormat producerDataFormat;
    private DataFormat consumerDataFormat;

    public DataFormatBinding() {
    }

    public DataFormatBinding(DataFormat dataFormat) {
        this(dataFormat, dataFormat);
    }

    public DataFormatBinding(DataFormat consumerDataFormat, DataFormat producerDataFormat) {
        this.consumerDataFormat = consumerDataFormat;
        this.producerDataFormat = producerDataFormat;
    }

    @Override
    public Processor createProduceProcessor() {
        ObjectHelper.notNull(producerDataFormat, "producerDataFormat");
        return new MarshalProcessor(producerDataFormat);
    }

    @Override
    public Processor createConsumeProcessor() {
        ObjectHelper.notNull(consumerDataFormat, "consumerDataFormat");
        return new UnmarshalProcessor(consumerDataFormat);
    }

    /**
     * Sets the data format for both producer and consumer sides
     */
    public void setDataFormat(DataFormat dataFormat) {
        setConsumerDataFormat(dataFormat);
        setProducerDataFormat(dataFormat);
    }

    public DataFormat getConsumerDataFormat() {
        return consumerDataFormat;
    }

    public void setConsumerDataFormat(DataFormat consumerDataFormat) {
        this.consumerDataFormat = consumerDataFormat;
    }

    public DataFormat getProducerDataFormat() {
        return producerDataFormat;
    }

    public void setProducerDataFormat(DataFormat producerDataFormat) {
        this.producerDataFormat = producerDataFormat;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
