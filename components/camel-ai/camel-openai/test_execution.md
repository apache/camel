## Test execution

### MacOS or Linux without nvidia graphic card
If ollama is already installed on the system execute the test with

```bash
mvn verify -Dollama.endpoint=http://localhost:11434/ -Dollama.model=granite4:3b -Dollama.instance.type=remote
```

The Ollama docker image is really slow on macbook without nvidia hardware acceleration

### Linux with Nvidia graphic card
The hardware acceleration can be used, and the test can be executed with

```bash
mvn verify -Dollama.container.enable.gpu=enabled
```

### Embedding tests

The embedding integration tests require an embedding model. By default, the test infrastructure uses `granite-embedding:30m`.

To run embedding tests with a local Ollama instance:

```bash
mvn verify -Dollama.endpoint=http://localhost:11434/ -Dollama.model=granite4:3b -Dollama.embedding.model=granite-embedding:30m -Dollama.instance.type=remote
```

Make sure the embedding model is pulled in Ollama before running the tests:

```bash
ollama pull granite-embedding:30m
```

### Parallel MCP tool execution tests

`OpenAIMcpParallelToolExecutionIT` covers `parallelToolExecution=true` end to end. The parallel dispatch path is
only reached when the model emits more than one tool call in a single response, since a batch of one is executed
inline. That is the model's decision, not the component's, so the backend matters:

* `qwen3.5:2b-mlx` reliably emits two-call batches for these prompts, and the MLX build is noticeably faster on
  Apple Silicon.
* Smaller or older models frequently serialise the same prompt into one tool call per iteration, in which case the
  tests still pass but only cover the inline path.

```bash
ollama pull qwen3.5:2b-mlx
mvn verify -Dollama.endpoint=http://localhost:11434/ -Dollama.model=qwen3.5:2b-mlx -Dollama.instance.type=remote \
  -Dit.test=OpenAIMcpParallelToolExecutionIT
```

To confirm the parallel path was actually exercised, raise
`org.apache.camel.component.openai.McpToolCallExecutor` to `DEBUG` and look for
`Executing N tool call(s) in parallel` in `target/camel-openai-tests.log`.

The deterministic proof that a batch is dispatched concurrently is in the unit tests
(`McpToolCallExecutorTest`, `OpenAIParallelToolExecutionTest`), which do not need Ollama at all.

Note that agentic ITs asserting the model called a tool at all can flake with small models, which sometimes answer
directly instead of calling a tool.

### Running with OpenAI

To run tests against OpenAI API instead of Ollama:

```bash
mvn verify -Dollama.instance.type=openai -Dopenai.api.key=sk-xxx -Dopenai.model=gpt-4o-mini -Dopenai.embedding.model=text-embedding-ada-002
```

Or using environment variables:

```bash
export OPENAI_API_KEY=sk-xxx
export OPENAI_MODEL=gpt-4o-mini
export OPENAI_EMBEDDING_MODEL=text-embedding-ada-002
mvn verify -Dollama.instance.type=openai
```

### Opt-in external audio, image, and moderation tests

`OpenAIAudioExternalServiceIT`, `OpenAIImageExternalServiceIT`, and
`OpenAIModerationExternalServiceIT` are disabled by default. They exercise the real HTTP
contract of the audio, image, and moderation operations against an endpoint that implements
the corresponding OpenAI API. Enable an individual test with `-Dopenai.live.tests=true`.

They are also executable examples of the Camel API: configure `openai` once, send the natural
body to a `direct:` endpoint, and route it to `openai:<operation>`. The audio test feeds the
speech result directly into transcription and translation; the image test feeds generation
directly into edit. No OpenAI SDK calls appear in route or test code.

The audio and image tests write returned binary artifacts to `target/openai-live/` (`speech.wav`,
`generated.png`, and `edited.png`) so they can be inspected after the test completes.

All tests need `openai.live.baseUrl`; `openai.live.apiKey` is optional for local services and
defaults to `dummy`. These tests intentionally make only structural assertions because model
output is nondeterministic.

#### macOS Apple Silicon (M4 Max, 32 GB)

Run audio and image services separately: each `rapid-mlx serve` process keeps its model in
unified memory. Do not use `qwen-image-edit` on a 32 GB machine; use `flux2-klein-4b`, which
supports both generation and edit.

For audio, install Docker Desktop and `uv` (`brew install uv`), then download the CPU service
image and the two models used by the test. The models are cached in the Docker volume, so this is
only needed once. Pin the image to a digest when recording a reproducible result.

```bash
docker pull ghcr.io/speaches-ai/speaches:latest-cpu
docker run --rm --detach --publish 8000:8000 --name camel-speaches \
  --volume camel-speaches-models:/home/ubuntu/.cache/huggingface/hub \
  ghcr.io/speaches-ai/speaches:latest-cpu
export SPEACHES_BASE_URL=http://localhost:8000
uvx speaches-cli model download Systran/faster-distil-whisper-small.en
uvx speaches-cli model download speaches-ai/Kokoro-82M-v1.0-ONNX
```

Then run:

```bash
mvn verify -Dit.test=OpenAIAudioExternalServiceIT -Dopenai.live.tests=true \
  -Dopenai.live.baseUrl=http://localhost:8000/v1 \
  -Dopenai.live.audio.model=Systran/faster-distil-whisper-small.en \
  -Dopenai.live.speech.model=speaches-ai/Kokoro-82M-v1.0-ONNX \
  -Dopenai.live.speech.voice=af_heart
```

For images, install the image runtime. Starting the server downloads the `flux2-klein-4b` model
on its first use; it is the only local image model required by this test.

```bash
pip install 'rapid-mlx[image]'
rapid-mlx serve flux2-klein-4b
mvn verify -Dit.test=OpenAIImageExternalServiceIT -Dopenai.live.tests=true \
  -Dopenai.live.baseUrl=http://localhost:8000/v1 \
  -Dopenai.live.image.model=flux2-klein-4b
```

For local moderation, install the native macOS LocalAI application or CLI (do not use Docker on
Apple Silicon), then start an instruction model. LocalAI exposes `POST /v1/moderations` and
constrains the model output to the OpenAI moderation response schema. Install LocalAI's
documented `qwen3-4b` gallery model as the compatibility baseline for this test. It is a general
instruction model, not a trained moderation model; this test validates the HTTP contract, not
safety-classification quality. When using the LocalAI UI, use the exact installed model ID in
`openai.live.moderation.model`.

Dedicated classifiers such as Llama Guard 3 1B/8B and ShieldGemma are better candidates when
evaluating local moderation quality, but must first be validated with LocalAI's endpoint. Their
native safe/unsafe output and hazard taxonomies differ from OpenAI's category schema, so they
are not drop-in proof that the OpenAI-compatible response is semantically equivalent.

```bash
# If installed from the UI, start LocalAI and keep it running instead.
local-ai run qwen3-4b
mvn verify -Dit.test=OpenAIModerationExternalServiceIT -Dopenai.live.tests=true \
  -Dopenai.live.baseUrl=http://localhost:8089/v1 \
  -Dopenai.live.moderation.model=qwen3-4b
```

Ollama guard models, upstream llama.cpp, vLLM, and MLX servers do not themselves implement the
OpenAI moderation endpoint. For a provider acceptance check, use OpenAI instead:

```bash
mvn verify -Dit.test=OpenAIModerationExternalServiceIT -Dopenai.live.tests=true \
  -Dopenai.live.baseUrl=https://api.openai.com/v1 \
  -Dopenai.live.apiKey="$OPENAI_API_KEY" \
  -Dopenai.live.moderation.model=omni-moderation-latest
```

### Opt-in Responses API tests

`OpenAIResponsesExternalServiceIT` exercises the `responses`, `responses-retrieve` and `responses-cancel` operations
against an endpoint that implements `POST /v1/responses`: instructions and developer messages, structured output,
conversation memory, route tools, background mode, retrieval and cancellation. `OpenAIResponsesMcpExternalServiceIT`
runs the tool loop against the MCP Everything server, which it starts as a container, so it also needs Docker. Like
the tests above they are disabled by default and enabled with `-Dopenai.live.tests=true`.

Local servers implement different parts of the Responses API, so the backend matters:

* vLLM implements stored responses (`previous_response_id`), function tools and background mode, which makes it the
  local baseline for these tests.
* Ollama implements only the stateless subset: no `previous_response_id`, no conversations, no background mode.
* LM Studio implements `previous_response_id` and function tools, but not background mode.
* Hosted tools (`web_search`, `file_search`, `code_interpreter` and hosted MCP), citations and the Conversations API
  only exist on OpenAI. Those behaviours are covered by `OpenAIResponsesMockTest` on the OpenAI mock instead.

#### macOS Apple Silicon with vLLM Metal

Install [vLLM Metal](https://docs.vllm.ai/projects/vllm-metal/en/latest/installation/), which creates the
`~/.venv-vllm-metal` virtual environment, then download a model. `Qwen3-4B-Instruct-2507` is small enough for a
32 GB machine, calls tools reliably and does not emit `<think>` blocks.

```bash
source ~/.venv-vllm-metal/bin/activate
hf download mlx-community/Qwen3-4B-Instruct-2507-4bit
```

Start the server. `VLLM_ENABLE_RESPONSES_API_STORE=1` enables stored responses, which `previous_response_id` and
background mode need. vLLM keeps them in memory and never evicts them, so restart the server between long test
sessions. `--enable-auto-tool-choice --tool-call-parser hermes` enables function tools for Qwen3.

```bash
VLLM_ENABLE_RESPONSES_API_STORE=1 vllm serve mlx-community/Qwen3-4B-Instruct-2507-4bit \
  --enable-auto-tool-choice --tool-call-parser hermes --max-model-len 8192 --port 8000
```

Then run:

```bash
mvn verify -Dit.test='OpenAIResponses*ExternalServiceIT' -Dopenai.live.tests=true \
  -Dopenai.live.baseUrl=http://localhost:8000/v1 \
  -Dopenai.live.responses.model=mlx-community/Qwen3-4B-Instruct-2507-4bit
```

The assertions check the content of short, deterministic answers (`temperature=0`), which a 4B model gets right, but
they can still flake with smaller models.
