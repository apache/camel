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
package org.apache.camel.component.jt400;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;

import javax.naming.OperationNotSupportedException;

import com.ibm.as400.access.AS400;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;

public class Jt400PgmEndpoint extends DefaultEndpoint {

    private String programToExecute;

    private Integer[] outputFieldsIdxArray;
    private Integer[] outputFieldsLengthArray;

    private AS400 iSeries;

    /**
     * Creates a new AS/400 PGM CALL endpoint
     */
    protected Jt400PgmEndpoint(String endpointUri, Jt400Component component) throws CamelException {
        super(endpointUri, component);
        try {
            URI uri = new URI(endpointUri);
            String[] credentials = uri.getUserInfo().split(":");
            iSeries = new AS400(uri.getHost(), credentials[0], credentials[1]);
            programToExecute = uri.getPath();
        } catch (URISyntaxException e) {
            throw new CamelException("Unable to parse URI for " + endpointUri, e);
        }
    }

    public Jt400PgmEndpoint(String endpointUri, String programToExecute, Map<String, Object> parameters,
                            CamelContext camelContext) {
        super(endpointUri, camelContext);
        this.programToExecute = programToExecute;
    }

    public Producer createProducer() throws Exception {
        return new Jt400PgmProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new OperationNotSupportedException();
    }

    public boolean isSingleton() {
        return false;
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (iSeries != null) {
            iSeries.disconnectAllServices();
        }
    }

    public boolean isFieldIdxForOuput(int idx) {
        return Arrays.binarySearch(outputFieldsIdxArray, idx) >= 0;
    }

    public int getOutputFieldLength(int idx) {
        return outputFieldsLengthArray[idx];
    }

    // getters and setters
    public String getProgramToExecute() {
        return programToExecute;
    }

    public AS400 getiSeries() {
        return iSeries;
    }

    public void setOutputFieldsIdx(String outputFieldsIdx) {
        if (outputFieldsIdx != null) {
            String[] outputArray = outputFieldsIdx.split(",");
            outputFieldsIdxArray = new Integer[outputArray.length];
            for (int i = 0; i < outputArray.length; i++) {
                String str = outputArray[i];
                outputFieldsIdxArray[i] = Integer.parseInt(str);
            }
        }
    }

    public void setFieldsLength(String fieldsLength) {
        if (fieldsLength != null) {
            String[] outputArray = fieldsLength.split(",");
            outputFieldsLengthArray = new Integer[outputArray.length];
            for (int i = 0; i < outputArray.length; i++) {
                String str = outputArray[i];
                outputFieldsLengthArray[i] = Integer.parseInt(str);
            }
        }
    }

}
