# AWS S3 Example - CDI

### Introduction

This example illustrates the integration between Camel, CDI and AWS S3.

The `camel-cdi` and `camel-aws-s3` components are used in this example..

Don't forget to add your bucket name and your credentials in the Application.java file before compiling. Actually AccessKey and SecretKey are equals to 'XXXXXX', while the bucketName in the route is named 'bucketName'

### Build

You can build this example using:

```sh
$ mvn package
```

### Run

You can run this example using:

```sh
$ mvn camel:run
```

When the Camel application runs, you should the files from your bucket been downloaded and saved into target/s3out directory with their filename.

The Camel application can be stopped pressing <kbd>ctrl</kbd>+<kbd>c</kbd> in the shell.

### Forum, Help, etc

If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may have. Enjoy!

The Camel riders!
