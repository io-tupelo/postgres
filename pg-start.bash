#!/bin/bash

docker run --rm  --name pg-docker  --env POSTGRES_PASSWORD=docker  --detach  --publish 5432:5432  \
  -v $HOME/docker/volumes/postgres:/var/lib/postgresql/data    postgres:latest

