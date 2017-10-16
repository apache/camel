Thrift tutorial java files generation
-----------------------
$ cd src/test/thrift

$ thrift -r --gen java -out ../java/ ./tutorial-dataformat.thrift

$ thrift -r --gen java -out ../java/ ./tutorial-component.thrift

*Examples taken from the Apache Thrift java tutorial https://thrift.apache.org/tutorial/java*