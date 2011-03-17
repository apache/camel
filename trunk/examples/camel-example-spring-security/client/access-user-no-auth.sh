#!/bin/bash
rm -rf work
mkdir work
wget --directory-prefix=work  --output-file=work/log.txt http://localhost:8080/camel/user
cat -n work/* | less
