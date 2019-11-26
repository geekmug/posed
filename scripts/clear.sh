#!/bin/bash

echo '{"frame": "GCRF", "recursive": true}' | evans -r -p 8080 --cli --call Delete | jq -c
