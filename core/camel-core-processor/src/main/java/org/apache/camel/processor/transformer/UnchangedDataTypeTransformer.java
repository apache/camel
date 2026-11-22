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
package org.apache.camel.processor.transformer;

import org.apache.camel.Message;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;

/**
 * A data type that leaves the message as it is: the body, its type and the headers, with no Content-Type set. It is for
 * a Kamelet that leaves the body as it is or does not read it, such as a filter or an action that sets headers, so it
 * can declare its in and out data types, which tooling such as the YAML validator reads.
 */
@DataTypeTransformer(name = "application-x-unchanged",
                     description = "Leaves the body and the headers as they are")
@BodyTypes(transformerIn = @BodyType(value = Carrier.ANY, description = "Any body, which is not read"),
           transformerOut = @BodyType(value = Carrier.UNCHANGED, description = "The body as it was"))
public class UnchangedDataTypeTransformer extends Transformer {

    @Override
    public void transform(Message message, DataType from, DataType to) throws Exception {
        // the message is left as it is
    }
}
