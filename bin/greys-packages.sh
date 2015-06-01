#!/bin/sh

# package first
./package.sh

mkdir -p ../target/greys
cp greys.sh ../target/greys
cp -r ../scripts ../target/greys/
cp ../target/greys-anatomy-jar-with-dependencies.jar ../target/greys/greys.jar
cd ../target/
zip -r greys.zip greys/
cd -

