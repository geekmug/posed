#!/bin/sh
PORT=${1:-8080}
CLIENTS=${2:-20}
HZ=${3:-30}
RPS=$((CLIENTS * HZ))
echo '{"frame": "base"}' | evans -r -p ${PORT} cli call CreateRoot | jq -c
echo '{"frame": "base", "geopose": {"position": {"latitude": 39.177800, "longitude": -86.589451, "amsl": 272}, "angles": {"roll": 0, "pitch": 0, "yaw": 0}}}' | evans -r -p ${PORT} cli call Update | jq -c
ghz \
  --duration=60s --concurrency=$CLIENTS --rps=$RPS \
  --data='{"frame": "base", "pose": {"cartesian": {}, "angles": {}}}' \
  --proto=$(dirname "$0")/../posed-protos/src/main/proto/posed.proto \
  --call posed.PoseService.ConvertLocal \
  --insecure \
  localhost:${PORT}
