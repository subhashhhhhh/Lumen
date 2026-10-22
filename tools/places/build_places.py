"""Trim GeoNames cities to: lat, lon, name, region index, country code. Regions deduped into a header table."""
import sys
src, out = sys.argv[1], sys.argv[2]
adm = {}
for l in open('admin1CodesASCII.txt', encoding='utf-8'):
    c = l.rstrip('\n').split('\t'); adm[c[0]] = c[1]
regions, region_idx, rows = [], {}, []
for l in open(src, encoding='utf-8'):
    c = l.rstrip('\n').split('\t')
    name, lat, lon, cc, a1 = c[1], float(c[4]), float(c[5]), c[8], c[10]
    region = adm.get(f"{cc}.{a1}", '')
    if region not in region_idx:
        region_idx[region] = len(regions); regions.append(region)
    rows.append(f"{lat:.4f}\t{lon:.4f}\t{name}\t{region_idx[region]}\t{cc}")
with open(out, 'w', encoding='utf-8') as f:
    f.write(f"{len(regions)}\t{len(rows)}\n")
    f.write('\n'.join(regions) + '\n')
    f.write('\n'.join(rows) + '\n')
