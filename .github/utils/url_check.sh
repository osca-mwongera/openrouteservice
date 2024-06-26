#!/bin/bash
host=${1}
port=${2}
path=${3}
http_code=${4}
timeout=${5}
# default to 2 sec sleep timer
sleep=${6:-2}
# default to reporting every 10 executions
report_every=${7:-10}

function wait_for_url() {
  local url="$1"
  local timeout_sec="$2"
  local expected_http_code="$3"
  local sleep="$4"
  local report_every="$5"

  start_time=$(date +%s)
  turn=0
  while true; do
    response=$(curl -s -o /dev/null -w "%{http_code}" "${url}")
    if [[ "$response" == "${expected_http_code}" ]]; then
      echo "Request succeeded for ${url} with expected http code ${expected_http_code}"
      return 0
    fi
    current_time=$(date +%s)
    elapsed_time=$((current_time - start_time))
    remaining_time=$(( timeout_sec - elapsed_time))
    if ((elapsed_time >= timeout_sec)); then
      echo "Timed out waiting for response after ${timeout_sec} seconds for ${url}"
      return 1
    fi
    if [ $(( turn % report_every )) -eq "0" ]; then
      echo "GET request failed with response code ${response} for ${url}, retrying for another ${remaining_time} seconds..."
    fi
    (( turn+=1 ))
    sleep "$sleep"
  done
}

wait_for_url "$host:$port$path" "${timeout}" "${http_code}" "${sleep}" "${report_every}"
