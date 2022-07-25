#!/bin/bash 

# vvv The first line starts a Docker container running Postgres
docker run -it  --rm  --network host  --env PGPASSWORD=docker  postgres:latest  \
  psql -U postgres -h localhost  -p 5432  "$@"      # <= 2nd line invokes `psql` within the container


