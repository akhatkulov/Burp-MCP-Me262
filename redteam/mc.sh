#!/bin/bash
# Me262 MCP call helper — authorised VulnBank/RedEye red-team engagement.
# usage: mc.sh <tool> '<json-args>' [wait_seconds]
TOOL="$1"; ARGS="$2"; [ -z "$ARGS" ] && ARGS='{}'; WAIT="${3:-10}"
SSE=$(mktemp)
stdbuf -oL curl -sN -m $((WAIT+8)) http://127.0.0.1:9262/ > "$SSE" 2>/dev/null &
P=$!
for i in $(seq 1 30); do l=$(grep -m1 'sessionId=' "$SSE" 2>/dev/null | tr -d '\r'); [ -n "$l" ] && { SID=$(printf '%s' "$l" | sed 's/^data: *//'); break; }; sleep 0.2; done
POST="http://127.0.0.1:9262/${SID}"
REQ="{\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"tools/call\",\"params\":{\"name\":\"$TOOL\",\"arguments\":$ARGS}}"
curl -s -m 8 -o /dev/null -X POST "$POST" -H 'Content-Type: application/json' -d "$REQ"
for i in $(seq 1 $WAIT); do grep -q '"id":99' "$SSE" && break; sleep 1; done
sleep 0.3; kill $P 2>/dev/null
grep '^data:' "$SSE" | sed 's/^data: *//' | tr -d '\r' | python3 -c "
import sys,json
for ln in sys.stdin:
    ln=ln.strip()
    if not ln.startswith('{'): continue
    try: m=json.loads(ln)
    except: continue
    if m.get('id')==99:
        r=m.get('result') or {}
        print(r['content'][0]['text'] if 'content' in r else 'ERROR: '+json.dumps(m.get('error')))
        break
"
rm -f "$SSE"
