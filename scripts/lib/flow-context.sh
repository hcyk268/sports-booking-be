#!/bin/sh
# Preserve the parent test's runtime settings across common.sh defaults.
flow_api_base=$api_base
flow_booking_date=$booking_date
flow_court_id=$court_id
flow_sepay_secret=${sepay_secret:-}
flow_ipn_wait_seconds=${ipn_wait_seconds:-4}
. "$SCRIPT_DIR/lib/common.sh"
api_base=$flow_api_base
booking_date=$flow_booking_date
court_id=$flow_court_id
sepay_secret=$flow_sepay_secret
ipn_wait_seconds=$flow_ipn_wait_seconds
