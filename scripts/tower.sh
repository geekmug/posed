#!/bin/bash

while true; do
  echo '{"frame": "base"}' | evans -r -p 8080 --cli --call CreateRoot | jq -c > /dev/null
  echo '{"frame": "base", "geopose": {"position": {"latitude": 39.177800, "longitude": -86.589451, "amsl": 272}, "angles": {"roll": 0, "pitch": 0, "yaw": 0}}}' | evans -r -p 8080 --cli --call Update | jq -c

  echo '{"frame": "tower", "parent": "base", "pose": {"cartesian": {"z": -30}, "angles": {}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "ge9181", "parent": "tower", "pose": {"cartesian": {}, "angles": {}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "tweak", "parent": "tower", "pose": {"cartesian": {}, "angles": {"yaw": -90}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "eoir", "parent": "tower", "pose": {"cartesian": {"z": 0.15}, "angles": {"yaw": 134.2}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "lensCleaner", "parent": "tower", "pose": {"cartesian": {"x": -0.5}, "angles": {"yaw": 180}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "moog1", "parent": "tower", "pose": {"cartesian": {"x": 1, "y": 2, "z": 1}, "angles": {"roll": 180, "yaw": -60}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "moog2", "parent": "tower", "pose": {"cartesian": {"x": 1, "y": -2, "z": 1}, "angles": {"roll": 180, "yaw": 60}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "r3d", "parent": "base", "pose": {"cartesian": {"y": 1, "z": -8}, "angles": {}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "r20", "parent": "tower", "pose": {"cartesian": {}, "angles": {"yaw": -9.5}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  echo '{"frame": "boomerang", "parent": "tower", "pose": {"cartesian": {}, "angles": {}}}' | evans -r -p 8080 --cli --package posed  --call Create >/dev/null
  sleep 1
done
