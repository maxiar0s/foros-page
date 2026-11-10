#!/bin/bash
# Oracle image runs this hook only while initializing a fresh data volume.
# Keep tracing disabled: APP_USER_PASSWORD must never be written to the logs.
set -eu

: "${APP_USER:?APP_USER must be configured}"
: "${APP_USER_PASSWORD:?APP_USER_PASSWORD must be configured}"

sqlplus -L -s /nolog <<SQL
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE
CONNECT "${APP_USER}"/"${APP_USER_PASSWORD}"@//localhost:1521/FREEPDB1
@/opt/forum/01-forum-schema-and-seed.sql
EXIT SUCCESS
SQL
