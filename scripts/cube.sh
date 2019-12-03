#!/bin/bash


yaw=0
while true; do
  yaw=$((yaw + 5 % 360))
  #yaw=0
  echo '{"frame": "A"}' | evans -r -p 8080 --cli --call CreateRoot | jq -c > /dev/null
  echo "{\"frame\": \"A\"," \
       "\"geopose\": {\"position\": {\"latitude\": 90, \"longitude\": 0, \"hae\": 2000}," \
       "\"angles\": {\"roll\": 0, \"pitch\": $yaw, \"yaw\": $yaw}}}" \
    | evans -r -p 8080 --cli --call Update | jq -c
  
  echo '{"parent": "A", "frame": "B", "pose": {"position": {"x": 0, "y": 100, "z": 0}, "angles": {}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"parent": "B", "frame": "C", "pose": {"position": {"x": 100, "y": 0, "z": 0}, "angles": {}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"parent": "C", "frame": "D", "pose": {"position": {"x": 0, "y": -100, "z": 0}, "angles": {}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"parent": "D", "frame": "E", "pose": {"position": {"x": 0, "y": 0, "z": -100}, "angles": {}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"parent": "E", "frame": "F", "pose": {"position": {"x": 0, "y": 100, "z": 0}, "angles": {}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"parent": "F", "frame": "G", "pose": {"position": {"x": -100, "y": 0, "z": 0}, "angles": {}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"parent": "G", "frame": "H", "pose": {"position": {"x": 0, "y": -100, "z": 0}, "angles": {}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  sleep 1
done
