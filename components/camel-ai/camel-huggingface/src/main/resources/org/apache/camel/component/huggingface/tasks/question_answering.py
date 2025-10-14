#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

from transformers import pipeline
from djl_python import Input, Output
import json
import torch
import logging

pipe = pipeline(
    task='question-answering',
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

        input_data = json.loads(input_str)  # {"question": "q", "context": "c"}

        result = pipe(question=input_data["question"], context=input_data["context"])

        # Extract "answer" as string
        answer = result["answer"] if "answer" in result else ''

        outputs = Output()
        outputs.add(answer, "data")
        return outputs

    except Exception as e:
        logging.error("Error in handle function: " + str(e))
        outputs = Output()
        outputs.add(json.dumps({"error": str(e)}), "data")
        return outputs
