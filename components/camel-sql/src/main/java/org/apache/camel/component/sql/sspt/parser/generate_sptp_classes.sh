#!/usr/bin/env bash
rm -f *.java
javacc sspt.jj
rm -f ParseException.java
