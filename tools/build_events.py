import json, sys, collections, datetime
d = json.load(open(sys.argv[1]))
known = {(176,176,176):'rest',(144,96,56):'bio',(160,208,192):'papier',(248,232,0):'gelb',(224,0,16):'schadstoff',(0,168,128):'gruen',(0,88,160):'baum'}
ev = collections.defaultdict(lambda: collections.defaultdict(list))  # type -> zone -> dates
for x in d:
    t = known.get(tuple(x['c'] or ()))
    if not t: continue
    date = datetime.date(2026, x['m'], x['d']).isoformat()
    label = x['t']
    zones = label.split('+') if t not in ('schadstoff','gruen') else ['alle']
    for z in zones:
        ev[t][z].append(date)
for t in ev:
    for z in ev[t]: ev[t][z] = sorted(set(ev[t][z]))
json.dump(ev, sys.stdout, indent=1)
