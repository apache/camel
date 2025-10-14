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
package org.apache.camel.component.huggingface;

import ai.djl.modality.Input;
import ai.djl.modality.Output;
import org.apache.camel.Exchange;
import org.apache.camel.component.huggingface.tasks.AbstractTaskPredictor;

public class TranslationPredictor extends AbstractTaskPredictor {

    @Override
    protected String getRequirements() {
        return """
                transformers>=4.30.0
                torch>=2.0.0
                accelerate
                sentencepiece
                """;
    }

    @Override
    protected String getPythonScript() {
        return """
                from transformers import pipeline
                from djl_python import Input, Output
                import json
                import torch
                import logging

                pipe = pipeline(
                    task='translation',
                    model='%s',
                    revision='%s',
                    device_map='%s'
                )

                def handle(inputs: Input):
                    try:
                        if inputs.content.size() == 0:
                            logging.info("Handling warmup call - returning empty output")
                            return Output()

                        input_str = inputs.get_as_string("data")

                        torch.manual_seed(42)

                        result = pipe(input_str)

                        # Extract translated_text
                        translated_text = result[0]['translation_text'] if result and isinstance(result, list) and 'translation_text' in result[0] else ''

                        outputs = Output()
                        outputs.add(translated_text, "data")
                        return outputs

                    except Exception as e:
                        logging.error("Error in handle function:", str(e))
                        outputs = Output()
                        outputs.add(json.dumps({"error": str(e)}), "data")
                        return outputs
                """
                .formatted(config.getModelId(), config.getRevision(), config.getDevice());
    }

    @Override
    protected Input prepareInput(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        if (!(body instanceof String)) {
            throw new IllegalArgumentException("Body must be String for TRANSLATION");
        }
        Input input = new Input();
        input.add("data", ((String) body).getBytes("UTF-8"));
        return input;
    }

    @Override
    protected void processOutput(Exchange exchange, Output output) throws Exception {
        String result = output.getAsString("data");
        exchange.getMessage().setBody(result);
        exchange.getMessage().setHeader(HuggingFaceConstants.OUTPUT, result);
    }
}
