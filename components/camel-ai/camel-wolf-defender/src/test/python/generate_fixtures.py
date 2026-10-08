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

"""Regenerate the small, synthetic fixtures: pip install onnx==1.20.1 tokenizers==0.22.2."""
from pathlib import Path

import onnx
from onnx import TensorProto, helper
from tokenizers import Tokenizer, models, pre_tokenizers, processors

output = Path(__file__).resolve().parents[1] / "resources"
output.mkdir(parents=True, exist_ok=True)
tokenizer = Tokenizer(models.WordLevel({"<unk>": 0, "<pad>": 1, "<bos>": 2, "<eos>": 3,
                                      "hello": 4, "attack": 5}, unk_token="<unk>"))
tokenizer.pre_tokenizer = pre_tokenizers.Whitespace()
tokenizer.post_processor = processors.TemplateProcessing(
    single="<bos> $A <eos>", special_tokens=[("<bos>", 2), ("<eos>", 3)])
# Deliberately encode a truncation policy. Java must disable it, then reject oversized text.
tokenizer.enable_truncation(max_length=4)
tokenizer.save(str(output / "tokenizer.json"))

inputs = [helper.make_tensor_value_info(name, TensorProto.INT64, ["batch", "sequence"])
          for name in ["input_ids", "attention_mask"]]
nodes = [helper.make_node("Mul", ["input_ids", "attention_mask"], ["masked"]),
         helper.make_node("Cast", ["masked"], ["floats"], to=TensorProto.FLOAT),
         helper.make_node("ReduceSum", ["floats", "axis"], ["sum"], keepdims=1),
         helper.make_node("Neg", ["sum"], ["negative"]),
         helper.make_node("Concat", ["negative", "sum"], ["logits"], axis=1)]
graph = helper.make_graph(nodes, "camel-wolf-defender-fixture", inputs,
                          [helper.make_tensor_value_info("logits", TensorProto.FLOAT, ["batch", 2])],
                          [helper.make_tensor("axis", TensorProto.INT64, [1], [1])])
model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 17)], ir_version=9)
onnx.checker.check_model(model)
onnx.save(model, output / "classifier.onnx.bin")
# Exercise the same native termination mechanism as production, using a long-running Loop.
body = helper.make_graph(
    [helper.make_node("Identity", ["condition_in"], ["condition_out"]),
     helper.make_node("Add", ["value_in", "one"], ["value_out"])], "loop-body",
    [helper.make_tensor_value_info("iteration", TensorProto.INT64, []),
     helper.make_tensor_value_info("condition_in", TensorProto.BOOL, []),
     helper.make_tensor_value_info("value_in", TensorProto.FLOAT, [])],
    [helper.make_tensor_value_info("condition_out", TensorProto.BOOL, []),
     helper.make_tensor_value_info("value_out", TensorProto.FLOAT, [])],
    [helper.make_tensor("one", TensorProto.FLOAT, [], [1])])
loop = helper.make_model(helper.make_graph(
    [helper.make_node("Loop", ["iterations", "condition", "initial"], ["value"], body=body)], "cancellation-fixture", [],
    [helper.make_tensor_value_info("value", TensorProto.FLOAT, [])],
    [helper.make_tensor("initial", TensorProto.FLOAT, [], [0]),
     helper.make_tensor("iterations", TensorProto.INT64, [], [1000000000]),
     helper.make_tensor("condition", TensorProto.BOOL, [], [True])]),
    opset_imports=[helper.make_opsetid("", 17)], ir_version=9)
onnx.checker.check_model(loop)
onnx.save(loop, output / "cancellation.onnx.bin")
