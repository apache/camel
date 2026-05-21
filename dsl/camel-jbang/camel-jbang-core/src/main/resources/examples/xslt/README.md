# XSLT Transformation

This example shows a basic XML transformation using XSLT style sheet.

## How to run

You can run this example using:

```sh
camel run *
```

This reads the XML input file from `./input/account.xml` and applies XSL transformation.

## Live updates of message transformation

You can do live changes to the stylesheet and see the output in real-time with Camel JBang by running:

```sh
camel transform message --body=file:input/account.xml --component=xslt --template=file:stylesheet.xsl --pretty --watch
```

You can then edit the `stylesheet.xsl` file, and save the file, and watch the terminal for updated result.

## Run directly from GitHub

The example can also be run directly by referring to the GitHub URL as shown:

```sh
camel run https://github.com/apache/camel-jbang-examples/tree/main/xslt
```
