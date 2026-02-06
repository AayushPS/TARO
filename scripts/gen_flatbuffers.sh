#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCHEMA="${ROOT_DIR}/src/main/resources/flatbuffers/taro_model.fbs"
JAVA_OUT_TMP="$(mktemp -d)"
PY_OUT="${ROOT_DIR}/src/main/resources/flatbuffers"
JAVA_OUT_FINAL="${ROOT_DIR}/src/main/java/org/Aayush/serialization/flatbuffers"

if ! command -v flatc >/dev/null 2>&1; then
  echo "flatc not found. Please install FlatBuffers compiler." >&2
  exit 1
fi

cleanup() {
  rm -rf "${JAVA_OUT_TMP}"
}
trap cleanup EXIT

# Generate Java + Python from schema (namespace remains taro.model)
flatc --java -o "${JAVA_OUT_TMP}" "${SCHEMA}"
flatc --python -o "${PY_OUT}" "${SCHEMA}"

# Rewrite Java package and relocate into org/Aayush/serialization/flatbuffers
# flatc outputs to taro/model/*.java based on schema namespace
taro_java_dir="${JAVA_OUT_TMP}/taro/model"
if [[ ! -d "${taro_java_dir}" ]]; then
  echo "Expected generated Java directory not found: ${taro_java_dir}" >&2
  exit 1
fi

mkdir -p "${JAVA_OUT_FINAL}/taro/model"
rm -rf "${JAVA_OUT_FINAL}/taro/model"/*

for f in "${taro_java_dir}"/*.java; do
  # Replace package line and any fully-qualified taro.model references
  sed \
    -e 's/^package taro\.model;/package org.Aayush.serialization.flatbuffers.taro.model;/' \
    -e 's/\btaro\.model\./org.Aayush.serialization.flatbuffers.taro.model./g' \
    "$f" > "${JAVA_OUT_FINAL}/taro/model/$(basename "$f")"
done

echo "FlatBuffers generation complete."
