#!/usr/bin/env bash

./mvnw clean compile package -DskipTests

cp -v src/main/resources/application.yml target/application.yml
cp -v README.md target/README.md

cd target

zip -9 contare-chafon.zip contare-chafon-module-1.0.0.jar application.yml README.md

cd -
