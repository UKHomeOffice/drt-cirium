#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

sbt clean scalafmtAll compile coverage test coverageOff coverageReport dependencyUpdates

