# Offline place names

`app/src/main/assets/places.tsv` is generated from [GeoNames](https://www.geonames.org/)
(CC BY 4.0) so Lumen can name photo locations without any network call.

Regenerate:

```sh
curl -O https://download.geonames.org/export/dump/cities5000.zip
curl -O https://download.geonames.org/export/dump/admin1CodesASCII.txt
unzip cities5000.zip
python3 build_places.py cities5000.txt ../../app/src/main/assets/places.tsv
```

Format: a header line `<regionCount>\t<placeCount>`, then one region (state/province) name per
line, then one place per line: `lat\tlon\tname\tregionIndex\tcountryCode`.
