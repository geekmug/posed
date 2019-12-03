#!/bin/bash


yaw=0
while true; do
  yaw=$((yaw + 5 % 360))
  #yaw=0
  echo '{"frame": "test"}' | evans -r -p 8080 --cli --call CreateRoot | jq -c > /dev/null
  echo "{\"frame\": \"test\"," \
       "\"geopose\": {\"position\": {\"latitude\": 39.177800, \"longitude\": -86.589451, \"hae\": 276}," \
       "\"angles\": {\"roll\": 0, \"pitch\": 0, \"yaw\": $yaw}}}" \
    | evans -r -p 8080 --cli --call Update | jq -c
  for i in $(seq 8); do
    rot=$((i * 45 % 360))
    #rot=0
    echo "{\"parent\": \"test\", \"frame\": \"child$i\", \"pose\": {\"position\": {\"x\": $i, \"y\": 0, \"z\": 0}, \"angles\": {\"roll\": 0, \"pitch\": 0, \"yaw\": $rot}}}" | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
#    echo "{\"parent\": \"test\", \"frame\": \"child$i\", \"pose\": {\"position\": {\"x\": 0, \"y\": $i, \"z\": 0}, \"angles\": {\"roll\": 0, \"pitch\": $rot, \"yaw\": 0}}}" | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
#    echo "{\"parent\": \"test\", \"frame\": \"child$i\", \"pose\": {\"position\": {\"x\": 0, \"y\": 0, \"z\": $i}, \"angles\": {\"roll\": 0, \"pitch\": $rot, \"yaw\": 0}}}" | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  done
  sleep 1
done
