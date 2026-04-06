# Sample Data For TARO Manual Testing

This directory contains public datasets prepared for TARO admin-flow testing.

There are two groups:

- `sample-data/manual/`: upload-ready CSVs for TARO admin-flow testing, including a large showcase dataset for audience demos

The repo only tracks the upload-ready files under `sample-data/manual/`.
Original downloads can be recreated locally from the source links below and are ignored by Git under `sample-data/external/`.
The synthetic showcase dataset is generated locally from a deterministic Python script that is tracked in the repo.

## Downloaded Sources

### 1. Hugging Face

Source page:
- `https://huggingface.co/datasets/ashwinb1999/Travel`

Direct file URL used:
- `https://huggingface.co/datasets/ashwinb1999/Travel/resolve/main/US%20Airline%20Flight%20Routes%20and%20Fares%201993-2024.csv?download=true`

Notes:
- This is a route-like airline market dataset with origin airport, destination airport, distance, passengers, and fare fields.
- The dataset page currently shows a viewer/schema issue because the repo mixes multiple CSV schemas, but the downloaded airline-route CSV file itself is usable.

Upload-ready subset:
- `sample-data/manual/hf_airline_routes_taro_demo.csv`

Suggested TARO training settings:
- target column: `average_fare`
- feature columns: `distance_miles`, `passengers`, `primary_market_share`, `low_fare_market_share`, `quarter`

Alternate target:
- `passengers`

### 2. UCI Machine Learning Repository

Source page:
- `https://archive.ics.uci.edu/dataset/492/metro+interstate+traffic+volume`

Direct file URL used:
- `https://archive.ics.uci.edu/static/public/492/metro%2Binterstate%2Btraffic%2Bvolume.zip`

Notes:
- This is an hourly traffic-volume dataset with weather and timestamp fields.
- It is good for a pure time-series training dry run where the target is traffic volume.

Upload-ready subset:
- `sample-data/manual/uci_metro_traffic_taro_demo.csv`

Suggested TARO training settings:
- target column: `traffic_volume`
- feature columns: `temp_k`, `rain_1h`, `snow_1h`, `clouds_all`

### 3. TomTom Traffic Index City Data

Source page:
- `https://www.tomtom.com/downloads/traffic-index/`

Direct file URL used:
- `https://download.tomtom.com/open/banners/new-york-city-us.zip`

Notes:
- TomTom publishes hourly city traffic CSVs with speed, free-flow speed, congestion, and travel time per 10 km.
- This is the closest fit to TARO's current "travel-time target + traffic features" admin flow.

Upload-ready subset:
- `sample-data/manual/tomtom_nyc_travel_time_taro_demo.csv`

Suggested TARO training settings:
- target column: `travel_time_per_10km_min`
- feature columns: `speed_kmh`, `free_flow_speed_kmh`, `congestion_level_pct`

### 4. TARO Synthetic Megacity Showcase Data

Source:
- generated locally with `scripts/generate_showcase_sample_data.py`

Notes:
- This is a deterministic synthetic traffic dataset built specifically for TARO demos.
- It models 12 corridors across 10 regions with hourly observations over 210 days.
- The file includes demand, weather, incidents, roadwork, event pressure, transit disruption, speed, congestion, and travel-time targets.
- It is designed to look richer on stage than the smaller smoke-test CSVs while still being upload-ready as a plain CSV.

Upload-ready file:
- `sample-data/manual/taro_megacity_multicorridor_showcase.csv`

Scale:
- 60,480 data rows
- 12 corridor pairs
- 10 region labels

Suggested TARO training settings:
- target column: `travel_time_per_10km_min`
- feature columns: `speed_kmh`, `free_flow_speed_kmh`, `congestion_level_pct`, `demand_index`, `incident_severity`, `event_intensity`, `rain_mm`, `snow_mm`, `transit_disruption_pct`, `hour_of_day`, `is_weekend`

Good storytelling columns for the admin preview table:
- `corridor_label`
- `region_label`
- `source_hub`
- `target_hub`

Regenerate locally:
- `python3 scripts/generate_showcase_sample_data.py`

## Recommended First Manual Test

Use:
- `sample-data/manual/tomtom_nyc_travel_time_taro_demo.csv`

Why:
- it is small enough for quick upload
- it has a clear numeric travel-time target
- it has obvious explanatory traffic features

## Recommended Audience Demo

Use:
- `sample-data/manual/taro_megacity_multicorridor_showcase.csv`

Why:
- it is large enough to look like a serious operational dataset
- it has richer features than the quick smoke datasets
- the corridor and hub labels tell a better story during a live walkthrough
- it still keeps a clear numeric target for TARO's current admin training flow

## Manual TARO Flow

1. Start Spring Boot:
   - `mvn spring-boot:run`
2. Open:
   - `http://127.0.0.1:8080/admin`
3. Set caller ID:
   - `manual-demo`
4. Upload:
   - `sample-data/manual/tomtom_nyc_travel_time_taro_demo.csv`
5. Set:
   - target column: `travel_time_per_10km_min`
   - feature columns: `speed_kmh`, `free_flow_speed_kmh`, `congestion_level_pct`
   - traits: keep the default recency / periodicity / persistence set
6. Create the training job.
7. Start the job.
8. Complete it manually with any release artifact id, for example:
   - `release-manual-demo-v1`
9. Publish it.
10. Open:
   - `http://127.0.0.1:8080/plan/manual-demo`
11. Keep the same caller ID.
12. The route form should unlock once the active model is published.

## Live Smoke Result

I verified one live API smoke with the TomTom subset:

- dataset upload accepted
- training job creation accepted

This confirms the downloaded CSV shape works with TARO's current admin upload and job-creation path.
