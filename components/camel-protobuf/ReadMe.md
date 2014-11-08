# How to upgrade protobuf

You need to install the protoc compiler from

    https://github.com/google/protobuf/releases

For linux/osx you download the .tar distro, and untar it, and then

    sudo ./configure
    sudo ./make check
    sudo ./make install

If its succesful, you can type

    protoc --version

To list the version of the proto compiler.

You then need to compile the sample test source for the `camel-protobuf` component.

The sample test source is an example taken from the Protobuf Java tutorial at: http://code.google.com/apis/protocolbuffers/docs/javatutorial.html

    cd components/camel-protobuf
	cd src/test/resources
	protoc --java_out=../java ./addressbook.proto

The generate source code will override the existing. You then need to insert back the checkstyle:off rule. For that take a git diff to see the code changes.

