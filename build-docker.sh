#!/bin/bash

set -o errexit

if [ "$#" -ne 1 ]; then
  echo "Incorrect parameters"
  echo "Usage: build-docker.sh <version>"
  exit 1
fi

VERSION=$1

PREFIX="docker.io/polarismesh"
SCRIPTDIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

pushd "$SCRIPTDIR/"
#java build the app.
docker run --rm -u root -v "$(pwd)":/home/maven/project -w /home/maven/project maven:3.8.1-openjdk-8-slim mvn clean package
#plain build -- no ratings
docker build --pull -t "${PREFIX}/polaris-sync:${VERSION}" -t "${PREFIX}/polaris-sync:latest" --build-arg version=${VERSION} .
docker push "${PREFIX}/polaris-sync:${VERSION}"
popd
