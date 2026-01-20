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