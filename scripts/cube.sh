#!/bin/bash
PORT=${1:-8080}
yaw=0
while true; do
  yaw=$((yaw + 5 % 360))
  #yaw=0
  echo '{"frame": "A"}' | evans -r -p ${PORT} cli call CreateRoot > /dev/null
  echo "{\"frame\": \"A\"," \
       "\"geopose\": {\"position\": {\"latitude\": 90, \"longitude\": 0, \"hae\": 2000}," \
       "\"angles\": {\"roll\": 0, \"pitch\": $yaw, \"yaw\": $yaw}}}" \
    | evans -r -p ${PORT} cli call Update | jq -c

  echo '{"parent": "A", "frame": "B", "pose": {"cartesian": {"x": 0, "y": 100, "z": 0}, "angles": {}}}' | evans -r -p ${PORT} cli call Create >/dev/null
  echo '{"parent": "B", "frame": "C", "pose": {"cartesian": {"x": 100, "y": 0, "z": 0}, "angles": {}}}' | evans -r -p ${PORT} cli call Create >/dev/null
  echo '{"parent": "C", "frame": "D", "pose": {"cartesian": {"x": 0, "y": -100, "z": 0}, "angles": {}}}' | evans -r -p ${PORT} cli call Create >/dev/null
  echo '{"parent": "D", "frame": "E", "pose": {"cartesian": {"x": 0, "y": 0, "z": -100}, "angles": {}}}' | evans -r -p ${PORT} cli call Create >/dev/null
  echo '{"parent": "E", "frame": "F", "pose": {"cartesian": {"x": 0, "y": 100, "z": 0}, "angles": {}}}' | evans -r -p ${PORT} cli call Create >/dev/null
  echo '{"parent": "F", "frame": "G", "pose": {"cartesian": {"x": -100, "y": 0, "z": 0}, "angles": {}}}' | evans -r -p ${PORT} cli call Create >/dev/null
  echo '{"parent": "G", "frame": "H", "pose": {"cartesian": {"x": 0, "y": -100, "z": 0}, "angles": {}}}' | evans -r -p ${PORT} cli call Create >/dev/null
  sleep 1
done
