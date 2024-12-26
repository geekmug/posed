#!/bin/bash
PORT=${1:-8080}
yaw=0
while true; do
  yaw=$((yaw + 5 % 360))
  #yaw=0
  echo '{"frame": "test"}' | evans -r -p ${PORT} cli call CreateRoot > /dev/null
  echo "{\"frame\": \"test\"," \
       "\"geopose\": {\"position\": {\"latitude\": 39.177800, \"longitude\": -86.589451, \"amsl\": 272}," \
       "\"angles\": {\"roll\": 0, \"pitch\": 0, \"yaw\": $yaw}}}" \
    | evans -r -p ${PORT} cli call Update | jq -c
  for i in $(seq 8); do
    rot=$((i * 45 % 360))
    #rot=0
    echo "{\"parent\": \"test\", \"frame\": \"child$i\", \"pose\": {\"cartesian\": {\"x\": $i, \"y\": 0, \"z\": 0}, \"angles\": {\"roll\": 0, \"pitch\": $rot, \"yaw\": 0}}}" | evans -r -p ${PORT} cli call Create >/dev/null
  done
  sleep 1
done
