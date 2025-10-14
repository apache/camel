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
import sys
import logging

pipe = pipeline(
    task='text-classification',
    model='%s',
    revision='%s',
    device_map='%s',
    top_k=%s
)

def handle(inputs: Input):
    try:
        if inputs.content.size() == 0:
            logging.info("Handling warmup call - returning empty output")
            return Output()
        input_str = inputs.get_as_string("data")
        torch.manual_seed(42)
        result = pipe(input_str)
        # If result is a list of lists, take the first one
        if result and isinstance(result[0], list):
            result = result[0]
        outputs = Output()
        outputs.add(json.dumps(result), "data")
        return outputs
    except Exception as e:
        logging.error("Error in handle function: " + str(e))
        outputs = Output()
        outputs.add(json.dumps({"error": str(e)}), "data")
        return outputs
