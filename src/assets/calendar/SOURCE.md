# Existing BibleDesktop calendar assets

These SVG/PNG files are unmodified copies of the files served by BibleDesktop
at `/assets/typikon/` and `/assets/markers/minimal-dark/` (commit `f3903f00`).
They are included in the existing PWA build so symbols remain available offline
without depending on cross-origin permissions for static server files.

The API still decides every date, rank, food rule and selected marker. The client
only resolves exact asset paths; it does not calculate or invent calendar data.
Unknown paths continue to use the API URL and report failed offline downloads.
Coordinate asset updates with the BibleDesktop project.
