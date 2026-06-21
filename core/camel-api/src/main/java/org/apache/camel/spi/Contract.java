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
package org.apache.camel.spi;

import java.util.Objects;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.jspecify.annotations.Nullable;

/**
 * Declares the expected input and/or output {@link DataType} of an {@link Endpoint} or {@link Processor}.
 * <p/>
 * When a route step declares a contract, Camel compares the message's actual {@link DataType} against the declared
 * input and output types and, if they differ, applies a matching {@link Transformer} to convert the payload; it can
 * also run a {@link Validator} when {@link #isValidateInput()} or {@link #isValidateOutput()} is set. This is the
 * mechanism behind declarative input/output typing on routes.
 * <p/>
 * See <a href="https://camel.apache.org/manual/transformer.html">Transformer</a> in the Camel user manual.
 *
 * @see DataType
 * @see Transformer
 * @see Validator
 */
public class Contract {

    private @Nullable DataType inputType;
    private @Nullable DataType outputType;
    private boolean validateInput;
    private boolean validateOutput;
    private @Nullable String contractString;

    /**
     * Gets the declared input data type.
     *
     * @return the input type, or <tt>null</tt> if none is declared
     */
    public @Nullable DataType getInputType() {
        return inputType;
    }

    /**
     * Set the input data type.
     *
     * @param inputType input data type
     */
    public void setInputType(String inputType) {
        Objects.requireNonNull(inputType, "inputType");
        this.inputType = new DataType(inputType);
        this.contractString = null;
    }

    /**
     * Set the input data type with Java class.
     *
     * @param clazz Java class which represents input data type
     */
    public void setInputType(Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz");
        this.inputType = new DataType(clazz);
        this.contractString = null;
    }

    /**
     * Gets the declared output data type.
     *
     * @return the output type, or <tt>null</tt> if none is declared
     */
    public @Nullable DataType getOutputType() {
        return outputType;
    }

    /**
     * Set the output data type.
     *
     * @param outputType output data type
     */
    public void setOutputType(String outputType) {
        Objects.requireNonNull(outputType, "outputType");
        this.outputType = new DataType(outputType);
        this.contractString = null;
    }

    /**
     * Set the output data type with Java class.
     *
     * @param clazz Java class which represents output data type
     */
    public void setOutputType(Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz");
        this.outputType = new DataType(clazz);
        this.contractString = null;
    }

    /**
     * Whether the input should be validated against the input data type.
     *
     * @return <tt>true</tt> if input validation is enabled
     */
    public boolean isValidateInput() {
        return validateInput;
    }

    /**
     * Whether to validate the input
     */
    public void setValidateInput(boolean validate) {
        this.validateInput = validate;
    }

    /**
     * Whether the output should be validated against the output data type.
     *
     * @return <tt>true</tt> if output validation is enabled
     */
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
    public boolean equals(@Nullable Object target) {
        if (!(target instanceof Contract)) {
            return false;
        }
        Contract targetContract = (Contract) target;
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
