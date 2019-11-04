#!/bin/bash

while true; do
  echo '{"frame": "base"}' | evans -r -p 8080 --cli --call CreateRoot | jq -c > /dev/null
  echo '{"frame": "base", "pose": {"position": {"latitude": 39.177800, "longitude": -86.589451, "altitude": 276}, "orientation": {"roll": 0, "pitch": 0, "yaw": 0}}}' | evans -r -p 8080 --cli --call Update | jq -c

  echo '{"frame": "tower", "parent": "base", "offset": {"position": {"z": -30}, "orientation": {}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "ge9181", "parent": "tower", "offset": {"position": {}, "orientation": {}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "tweak", "parent": "tower", "offset": {"position": {}, "orientation": {"yaw": -90}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "eoir", "parent": "tower", "offset": {"position": {"z": 0.15}, "orientation": {"yaw": 134.2}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "lensCleaner", "parent": "tower", "offset": {"position": {"x": -0.5}, "orientation": {"yaw": 180}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "moog1", "parent": "tower", "offset": {"position": {"x": 1, "y": 2, "z": 1}, "orientation": {"roll": 180, "yaw": -60}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "moog2", "parent": "tower", "offset": {"position": {"x": 1, "y": -2, "z": 1}, "orientation": {"roll": 180, "yaw": 60}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "r3d", "parent": "base", "offset": {"position": {"y": 1, "z": -8}, "orientation": {}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "r20", "parent": "tower", "offset": {"position": {}, "orientation": {"yaw": -9.5}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "boomerang", "parent": "tower", "offset": {"position": {}, "orientation": {}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  sleep 1
done
