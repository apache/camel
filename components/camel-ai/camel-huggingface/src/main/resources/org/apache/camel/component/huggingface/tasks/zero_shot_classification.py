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
    task='zero-shot-classification',
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
        input_data = json.loads(input_str)  # [text, label1, label2, ...]
        text = input_data[0]
        candidate_labels = input_data[1:]
        if not candidate_labels:
            raise ValueError("At least one candidate label required for zero-shot")
        torch.manual_seed(42)
        kwargs = {'multi_label': %s}
        result = pipe(text, candidate_labels=candidate_labels, **kwargs)
        outputs = Output()
        if %s:
            top_label = result['labels'][0]  # Highest score first (pipeline sorts descending)
            outputs.add(top_label, "data")
        else:
            outputs.add(json.dumps(result), "data")
        return outputs
    except Exception as e:
        logging.error("Error in handle function: " + str(e))
        outputs = Output()
        outputs.add(json.dumps({"error": str(e)}), "data")
        return outputs
