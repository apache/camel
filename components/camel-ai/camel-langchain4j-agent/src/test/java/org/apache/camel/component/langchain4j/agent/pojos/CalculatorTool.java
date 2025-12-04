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

package org.apache.camel.component.langchain4j.agent.pojos;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Calculator tools
 */
public class CalculatorTool {

    @Tool("Adds two numbers")
    public int add(@P("First number") int a, @P("Second number") int b) {
        return a + b;
    }

    @Tool("Multiplies two numbers")
    public int multiply(@P("First number") int a, @P("Second number") int b) {
        return a * b;
    }

    @Tool("Gets the square root of a number")
    public double sqrt(@P("Number") double x) {
        return Math.sqrt(x);
    }
}
