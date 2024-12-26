#!/bin/bash
PORT=${1:-8080}
echo '{"frame": "GCRF", "recursive": true}' | evans -r -p ${PORT} cli call Delete | jq -c
