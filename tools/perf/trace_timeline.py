"""
Slow main-thread frames after the first input in a Perfetto trace, with their biggest work.
Usage: python trace_timeline.py <trace.pftrace>   (needs `pip install perfetto`)
"""
import sys
from perfetto.trace_processor import TraceProcessor

PKG = "com.subhashh.lumen.debug"
tp = TraceProcessor(trace=sys.argv[1])
pid = list(tp.query(f"select pid from process where name='{PKG}'"))[0].pid
main = f"""select s.ts, s.dur, s.name, s.id, s.track_id from slice s join thread_track tt on s.track_id=tt.id
  join thread t using(utid) where t.tid={pid}"""
tap = list(tp.query(f"select min(ts) ts from ({main}) where name like '%input%'"))[0].ts
frames = list(tp.query(f"select ts, dur, track_id from ({main}) where name like 'Choreographer#doFrame%' and ts >= {tap} order by ts"))
print(f"frames after tap: {len(frames)}")
for f in frames:
    if f.dur < 12e6:
        continue
    kids = list(tp.query(f"""select name, dur/1e6 ms from slice where track_id={f.track_id} and ts>={f.ts}
      and ts+dur<={f.ts+f.dur} and depth between 1 and 3 and dur>3e6 order by dur desc limit 4"""))
    print(f"+{(f.ts-tap)/1e6:6.0f}ms  frame {f.dur/1e6:6.1f}ms  " + " | ".join(f"{k.name[:28]} {k.ms:.0f}" for k in kids))
