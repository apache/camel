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
package org.apache.camel.spi;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;

/**
 * A Contract which represents the input type and/or output type of the {@link Endpoint} or {@link Processor}.
 */
public class Contract {

    private DataType inputType;
    private DataType outputType;
    private boolean validateInput;
    private boolean validateOutput;
    private String contractString;
    
    public DataType getInputType() {
        return inputType;
    }
    
    /**
     * Set the input data type.
     *
     * @param inputType input data type
     */
    public void setInputType(String inputType) {
        this.inputType = new DataType(inputType);
        this.contractString = null;
    }
    
    /**
     * Set the input data type with Java class.
     *
     * @param clazz Java class which represents input data type
     */
    public void setInputType(Class<?> clazz) {
        this.inputType = new DataType(clazz);
        this.contractString = null;
    }
    
    public DataType getOutputType() {
        return outputType;
    }
    
   /**
    * Set the output data type.
    *
    * @param outputType output data type
    */
    public void setOutputType(String outputType) {
        this.outputType = new DataType(outputType);
        this.contractString = null;
    }
    
    /**
     * Set the output data type with Java class.
     *
     * @param clazz Java class which represents output data type
     */
    public void setOutputType(Class<?> clazz) {
        this.outputType = new DataType(clazz);
        this.contractString = null;
    }
    
    public boolean isValidateInput() {
        return validateInput;
    }

    /**
     * Whether to validate the input
     */
    public void setValidateInput(boolean validate) {
        this.validateInput = validate;
    }
    
    public boolean isValidateOutput() {
        return validateOutput;
    }

    /**
     * Whether to validate the output
     */
    public void setValidateOutput(boolean validate) {
        this.validateOutput = validate;
    }
    
    @Override
    public String toString() {
        if (contractString == null) {
            this.contractString = "DataType[input=" + this.inputType + ", output=" + this.outputType + "]";
        }
        return contractString;
    }

    public boolean isEmpty() {
        return inputType == null && outputType == null;
    }

    @Override
    public boolean equals(Object target) {
        if (!(target instanceof Contract)) {
            return false;
        }
        Contract targetContract = (Contract)target;
        if (getInputType() != null || targetContract.getInputType() != null) {
            if (getInputType() == null || targetContract.getInputType() == null
                || !getInputType().equals(targetContract.getInputType())) {
                return false;
            }
        }
        if (getOutputType() != null || targetContract.getOutputType() != null) {
            if (getOutputType() == null || targetContract.getOutputType() == null
                || !getOutputType().equals(targetContract.getOutputType())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
