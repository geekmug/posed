#!/bin/bash


yaw=0
while true; do
  yaw=$((yaw + 5 % 360))
  #yaw=0
  echo '{"frame": "A"}' | evans -r -p 8080 --cli --call CreateRoot | jq -c > /dev/null
  echo "{\"frame\": \"A\"," \
       "\"pose\": {\"position\": {\"latitude\": 90, \"longitude\": 0, \"altitude\": 2000}," \
       "\"orientation\": {\"roll\": 0, \"pitch\": $yaw, \"yaw\": $yaw}}}" \
    | evans -r -p 8080 --cli --call Update | jq -c
  
  echo "{\"parent\": \"A\", \"frame\": \"B\", \"offset\": {\"position\": {\"x\": 0, \"y\": 100, \"z\": 0}}}" | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo "{\"parent\": \"B\", \"frame\": \"C\", \"offset\": {\"position\": {\"x\": 100, \"y\": 0, \"z\": 0}}}" | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo "{\"parent\": \"C\", \"frame\": \"D\", \"offset\": {\"position\": {\"x\": 0, \"y\": -100, \"z\": 0}}}" | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo "{\"parent\": \"D\", \"frame\": \"E\", \"offset\": {\"position\": {\"x\": 0, \"y\": 0, \"z\": -100}}}" | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo "{\"parent\": \"E\", \"frame\": \"F\", \"offset\": {\"position\": {\"x\": 0, \"y\": 100, \"z\": 0}}}" | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo "{\"parent\": \"F\", \"frame\": \"G\", \"offset\": {\"position\": {\"x\": -100, \"y\": 0, \"z\": 0}}}" | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo "{\"parent\": \"G\", \"frame\": \"H\", \"offset\": {\"position\": {\"x\": 0, \"y\": -100, \"z\": 0}}}" | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  sleep 1
done
