#!/bin/bash

for frame in $(echo '{}' | evans -r -p 8080 --cli --call Traverse | jq -r '.frames[].frame' | tac); do
  echo "{\"frame\": \"$frame\"}" | evans -r -p 8080 --cli --call Delete | jq -c
done
