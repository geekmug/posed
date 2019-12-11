#!/bin/sh
echo '{"frame": "A", "pose": {"angles": {}}}' | ghz -z 10s -d @ -proto $(dirname "$0")/../posed-grpc/src/grpc/proto/posed.proto -call posed.PoseService.ConvertLocalToGeodetic -insecure localhost:8080
