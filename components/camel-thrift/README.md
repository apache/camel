# How to upgrade Apache Thrift

You need to install the thrift compiler from

    https://github.com/apache/thrift/releases

For linux/osx you download the .tar distro, and untar it, and then

    ./bootstrap.sh
    ./configure --disable-libs
    make
    sudo make install

If its succesful, you can type

    thrift --version

To display the version of the thrift compiler.

You then need to compile the sample test source for the `camel-thrift` component.

The sample test source is an example taken from the Thrift Java tutorial at: https://thrift.apache.org/tutorial/java

    cd components/camel-thrift
    cd src/test/thrift
    thrift -r --gen java -out ../java/ ./tutorial-dataformat.thrift
    thrift -r --gen java -out ../java/ ./tutorial-component.thrift

The generate source code will override the existing.

