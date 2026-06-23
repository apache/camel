## TUI Hello World

A simple example to try the TUI Send Message feature.

On startup, a welcome message is logged once. The `greet` route listens on `direct:greet` and replies with a greeting.

### How to run

    camel run tui-hello-world.yaml

### Send a message via TUI

    camel tui

Select the integration, press `F2`, choose `Send Message`, type your name (e.g. `World`), and press Enter.

Toggle the mode to `InOut` to see the reply (`Hello World!`).

### Send a message via CLI

    camel cmd send tui-hello-world --endpoint=direct:greet --body=World
