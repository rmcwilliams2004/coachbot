#!/bin/bash

check_return_code () {
    if [ $? -ne 0 ]
    then
        echo $1
        exit 1
    fi
}

lein spec -no-color
check_return_code "lein spec failed"

lein ancient :all :no-colors
check_return_code "lein ancient failed"

lein kibit
check_return_code "lein kibit failed"

lein bikeshed

echo "build successful"
exit 0