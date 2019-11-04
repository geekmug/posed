#!/bin/bash

while true; do
  echo '{"frame": "base"}' | evans -r -p 8080 --cli --call CreateRoot | jq -c > /dev/null
  echo "{\"frame\": \"base\"," \
       "\"pose\": {\"position\": {\"latitude\": 10, \"longitude\": -10, \"altitude\": 500}," \
       "\"orientation\": {\"roll\": 0, \"pitch\": 0, \"yaw\": 0}}}" \
    | evans -r -p 8080 --cli --call Update | jq -c

  echo '{"frame": "next", "parent": "base", "offset": {"orientation": {"pitch": 45, "yaw": 90}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "end", "parent": "next", "offset": {"orientation": {"pitch": -45, "yaw": -90}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  sleep 1
done
