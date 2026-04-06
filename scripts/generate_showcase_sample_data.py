#!/usr/bin/env python3
"""Generate a large, upload-ready TARO showcase dataset for audience demos."""

from __future__ import annotations

import csv
import math
import random
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path


START_AT = datetime(2025, 1, 1, 0, 0)
DAY_COUNT = 210
SEED = 20260406


@dataclass(frozen=True)
class Corridor:
    region_index: int
    region_label: str
    corridor_id: str
    corridor_label: str
    source_hub: str
    target_hub: str
    free_flow_speed_kmh: float
    baseline_flow_veh_per_h: int
    demand_bias: float
    weather_exposure: float
    incident_bias: float
    event_zone: bool
    roadwork_windows: tuple[tuple[datetime, datetime], ...]


def main() -> None:
    repo_root = Path(__file__).resolve().parents[1]
    output_path = (
        repo_root / "sample-data" / "manual" / "taro_megacity_multicorridor_showcase.csv"
    )
    output_path.parent.mkdir(parents=True, exist_ok=True)

    rows_written = write_dataset(output_path)
    print(f"Wrote {rows_written:,} rows to {output_path}")


def write_dataset(output_path: Path) -> int:
    rng = random.Random(SEED)
    corridors = build_corridors()
    fieldnames = [
        "record_id",
        "region_index",
        "region_label",
        "corridor_id",
        "corridor_label",
        "source_hub",
        "target_hub",
        "time",
        "day_of_week",
        "hour_of_day",
        "is_weekend",
        "month",
        "demand_index",
        "temp_c",
        "rain_mm",
        "snow_mm",
        "wind_kmh",
        "cloud_cover_pct",
        "visibility_km",
        "incident_count",
        "incident_severity",
        "event_intensity",
        "roadwork_active",
        "school_holiday",
        "transit_disruption_pct",
        "baseline_flow_veh_per_h",
        "observed_flow_veh_per_h",
        "speed_kmh",
        "free_flow_speed_kmh",
        "congestion_level_pct",
        "travel_time_per_10km_min",
        "travel_time_p90_per_10km_min",
        "travel_time_variability_pct",
    ]

    total_rows = 0
    with output_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()

        for day_offset in range(DAY_COUNT):
            for hour in range(24):
                timestamp = START_AT + timedelta(days=day_offset, hours=hour)
                for corridor in corridors:
                    row = generate_row(total_rows, timestamp, corridor, rng)
                    writer.writerow(row)
                    total_rows += 1

    return total_rows


def generate_row(
    record_index: int,
    timestamp: datetime,
    corridor: Corridor,
    rng: random.Random,
) -> dict[str, str | int | float]:
    hour = timestamp.hour
    day_of_week = timestamp.strftime("%A")
    is_weekend = timestamp.weekday() >= 5
    day_of_year = timestamp.timetuple().tm_yday

    morning_peak = gaussian_peak(hour, 8.0, 2.0)
    evening_peak = gaussian_peak(hour, 17.5, 2.3)
    lunch_peak = gaussian_peak(hour, 13.0, 2.8)
    weekend_peak = gaussian_peak(hour, 15.0, 3.5) if is_weekend else 0.0
    overnight_relief = gaussian_peak(hour, 3.0, 2.6)

    season_wave = math.sin((2.0 * math.pi * day_of_year) / 365.0)
    trend_wave = math.sin((2.0 * math.pi * day_of_year) / 45.0)
    temp_c = (
        15.0
        + 11.0 * season_wave
        + 3.3 * math.sin((2.0 * math.pi * (hour - 6)) / 24.0)
        + rng.uniform(-1.5, 1.5)
    )

    rain_mm = max(
        0.0,
        (
            rng.random() ** 3
            * (4.0 + 8.0 * corridor.weather_exposure)
            * (0.4 + max(season_wave, 0.0))
        ),
    )
    if rng.random() < 0.86:
        rain_mm *= 0.35

    snow_mm = 0.0
    if temp_c < 1.0 and timestamp.month in {1, 2, 12}:
        snow_mm = max(0.0, (rng.random() ** 2) * 3.2)

    cloud_cover_pct = clamp(
        25.0 + 8.0 * rain_mm + 11.0 * snow_mm + rng.uniform(0.0, 35.0),
        5.0,
        100.0,
    )
    wind_kmh = clamp(9.0 + 1.2 * rain_mm + rng.uniform(0.0, 18.0), 3.0, 42.0)
    visibility_km = clamp(15.0 - 0.9 * rain_mm - 1.4 * snow_mm + rng.uniform(-1.0, 1.5), 1.5, 18.0)

    event_intensity = event_intensity_for(timestamp, corridor)
    roadwork_active = 1 if is_roadwork_active(timestamp, corridor) else 0
    school_holiday = 1 if is_school_holiday(timestamp) else 0

    incident_count = 0
    incident_pressure = (
        0.24 * corridor.incident_bias
        + 0.22 * morning_peak
        + 0.28 * evening_peak
        + 0.08 * lunch_peak
        + 0.05 * weekend_peak
        + 0.03 * rain_mm
        + 0.06 * snow_mm
        + 0.12 * event_intensity
    )
    if rng.random() < min(0.78, incident_pressure):
        incident_count = 1 + int(rng.random() < min(0.45, incident_pressure / 1.4))
    incident_severity = round(
        min(1.0, 0.18 + 0.18 * incident_count + 0.04 * rain_mm + 0.07 * roadwork_active + rng.uniform(0.0, 0.22)),
        3,
    )

    demand_index = clamp(
        0.55
        + corridor.demand_bias
        + 0.7 * morning_peak
        + 0.78 * evening_peak
        + 0.15 * lunch_peak
        + 0.34 * weekend_peak
        + 0.08 * school_holiday
        + 0.06 * trend_wave
        - 0.12 * overnight_relief,
        0.35,
        2.45,
    )
    baseline_flow = corridor.baseline_flow_veh_per_h
    observed_flow = max(
        120,
        int(
            baseline_flow
            * demand_index
            * (0.94 + rng.uniform(-0.07, 0.09))
            * (1.0 + 0.08 * event_intensity)
        ),
    )

    congestion_pressure = clamp(
        0.12
        + 0.26 * demand_index
        + 0.08 * rain_mm * corridor.weather_exposure
        + 0.14 * snow_mm
        + 0.16 * incident_count * incident_severity
        + 0.11 * roadwork_active
        + 0.08 * event_intensity
        + 0.05 * (wind_kmh / 25.0)
        - 0.11 * overnight_relief,
        0.02,
        0.88,
    )
    speed_kmh = max(
        7.5,
        corridor.free_flow_speed_kmh * (1.0 - congestion_pressure) + rng.uniform(-2.2, 2.2),
    )
    congestion_level_pct = clamp(
        100.0 * (1.0 - speed_kmh / corridor.free_flow_speed_kmh),
        0.0,
        94.0,
    )
    travel_time_per_10km_min = round((10.0 / speed_kmh) * 60.0, 2)

    variability_pct = clamp(
        6.0
        + 18.0 * incident_count * incident_severity
        + 2.4 * rain_mm
        + 4.2 * snow_mm
        + 6.5 * roadwork_active
        + 5.5 * event_intensity
        + rng.uniform(0.0, 8.0),
        6.0,
        95.0,
    )
    travel_time_p90_per_10km_min = round(
        travel_time_per_10km_min * (1.0 + variability_pct / 100.0),
        2,
    )
    transit_disruption_pct = round(
        clamp(
            4.0
            + 14.0 * incident_count * incident_severity
            + 7.0 * event_intensity
            + 9.0 * roadwork_active
            + rng.uniform(0.0, 8.0),
            0.0,
            100.0,
        ),
        2,
    )

    return {
        "record_id": f"showcase-{record_index:06d}",
        "region_index": corridor.region_index,
        "region_label": corridor.region_label,
        "corridor_id": corridor.corridor_id,
        "corridor_label": corridor.corridor_label,
        "source_hub": corridor.source_hub,
        "target_hub": corridor.target_hub,
        "time": timestamp.strftime("%Y-%m-%dT%H:%M"),
        "day_of_week": day_of_week,
        "hour_of_day": hour,
        "is_weekend": int(is_weekend),
        "month": timestamp.month,
        "demand_index": round(demand_index, 3),
        "temp_c": round(temp_c, 2),
        "rain_mm": round(rain_mm, 2),
        "snow_mm": round(snow_mm, 2),
        "wind_kmh": round(wind_kmh, 2),
        "cloud_cover_pct": round(cloud_cover_pct, 1),
        "visibility_km": round(visibility_km, 2),
        "incident_count": incident_count,
        "incident_severity": incident_severity,
        "event_intensity": round(event_intensity, 3),
        "roadwork_active": roadwork_active,
        "school_holiday": school_holiday,
        "transit_disruption_pct": transit_disruption_pct,
        "baseline_flow_veh_per_h": baseline_flow,
        "observed_flow_veh_per_h": observed_flow,
        "speed_kmh": round(speed_kmh, 2),
        "free_flow_speed_kmh": round(corridor.free_flow_speed_kmh, 2),
        "congestion_level_pct": round(congestion_level_pct, 2),
        "travel_time_per_10km_min": travel_time_per_10km_min,
        "travel_time_p90_per_10km_min": travel_time_p90_per_10km_min,
        "travel_time_variability_pct": round(variability_pct, 2),
    }


def build_corridors() -> tuple[Corridor, ...]:
    return (
        Corridor(
            0,
            "Old Town Core",
            "C01",
            "Old Town Gate to River Market",
            "Old Town Gate",
            "River Market",
            42.0,
            1650,
            0.28,
            0.85,
            0.72,
            False,
            roadwork_windows=((datetime(2025, 2, 10), datetime(2025, 3, 7)),),
        ),
        Corridor(
            1,
            "North Heights",
            "C02",
            "Old Town Gate to Hill Junction",
            "Old Town Gate",
            "Hill Junction",
            48.0,
            1520,
            0.21,
            0.72,
            0.66,
            False,
            roadwork_windows=((datetime(2025, 5, 12), datetime(2025, 6, 5)),),
        ),
        Corridor(
            2,
            "Harbor Belt",
            "C03",
            "River Market to Harbor Exchange",
            "River Market",
            "Harbor Exchange",
            54.0,
            2140,
            0.34,
            1.0,
            0.84,
            True,
            roadwork_windows=((datetime(2025, 1, 20), datetime(2025, 2, 18)),),
        ),
        Corridor(
            3,
            "Harbor Belt",
            "C04",
            "Hill Junction to Harbor Exchange",
            "Hill Junction",
            "Harbor Exchange",
            52.0,
            1960,
            0.31,
            0.88,
            0.78,
            True,
            roadwork_windows=((datetime(2025, 4, 1), datetime(2025, 4, 24)),),
        ),
        Corridor(
            4,
            "Civic Spine",
            "C05",
            "Old Town Gate to Harbor Exchange Loop",
            "Old Town Gate",
            "Harbor Exchange",
            50.0,
            1880,
            0.29,
            0.79,
            0.74,
            False,
            roadwork_windows=((datetime(2025, 6, 16), datetime(2025, 7, 8)),),
        ),
        Corridor(
            5,
            "Entertainment Mile",
            "C06",
            "River Market to Stadium Mile",
            "River Market",
            "Stadium Mile",
            46.0,
            1760,
            0.24,
            0.76,
            0.81,
            True,
            roadwork_windows=((datetime(2025, 3, 18), datetime(2025, 4, 2)),),
        ),
        Corridor(
            6,
            "Entertainment Mile",
            "C07",
            "Stadium Mile to Harbor Exchange",
            "Stadium Mile",
            "Harbor Exchange",
            44.0,
            1940,
            0.32,
            0.82,
            0.86,
            True,
            roadwork_windows=((datetime(2025, 2, 24), datetime(2025, 3, 18)),),
        ),
        Corridor(
            7,
            "Campus Ridge",
            "C08",
            "Hill Junction to University Spur",
            "Hill Junction",
            "University Spur",
            57.0,
            1380,
            0.17,
            0.63,
            0.58,
            False,
            roadwork_windows=((datetime(2025, 1, 6), datetime(2025, 1, 28)),),
        ),
        Corridor(
            8,
            "Innovation Arc",
            "C09",
            "University Spur to Tech Park",
            "University Spur",
            "Tech Park",
            61.0,
            1490,
            0.22,
            0.58,
            0.52,
            False,
            roadwork_windows=((datetime(2025, 5, 26), datetime(2025, 6, 20)),),
        ),
        Corridor(
            9,
            "Logistics South",
            "C10",
            "Industrial Belt to Harbor Exchange",
            "Industrial Belt",
            "Harbor Exchange",
            58.0,
            2060,
            0.27,
            0.91,
            0.68,
            False,
            roadwork_windows=((datetime(2025, 4, 14), datetime(2025, 5, 9)),),
        ),
        Corridor(
            10,
            "Airport Causeway",
            "C11",
            "Airport Causeway to Harbor Exchange",
            "Airport Causeway",
            "Harbor Exchange",
            63.0,
            2240,
            0.36,
            0.95,
            0.73,
            True,
            roadwork_windows=((datetime(2025, 6, 2), datetime(2025, 7, 1)),),
        ),
        Corridor(
            11,
            "Riverside Freight Belt",
            "C12",
            "Riverside Belt to Industrial Docks",
            "Riverside Belt",
            "Industrial Docks",
            55.0,
            1825,
            0.25,
            0.84,
            0.64,
            False,
            roadwork_windows=((datetime(2025, 3, 3), datetime(2025, 3, 31)),),
        ),
    )


def event_intensity_for(timestamp: datetime, corridor: Corridor) -> float:
    if not corridor.event_zone:
        return 0.0

    sports_window = timestamp.weekday() in {2, 4, 5} and 17 <= timestamp.hour <= 22
    harbor_window = timestamp.weekday() in {0, 1, 3} and 7 <= timestamp.hour <= 10
    weekend_festival = timestamp.weekday() >= 5 and 12 <= timestamp.hour <= 21

    event_score = 0.0
    if sports_window and timestamp.timetuple().tm_yday % 3 == 0:
        event_score += 0.55
    if harbor_window and timestamp.timetuple().tm_yday % 5 == 0:
        event_score += 0.28
    if weekend_festival and timestamp.timetuple().tm_yday % 4 == 0:
        event_score += 0.34
    return clamp(event_score, 0.0, 1.0)


def is_roadwork_active(timestamp: datetime, corridor: Corridor) -> bool:
    for start, end in corridor.roadwork_windows:
        if start <= timestamp < end:
            return True
    return False


def is_school_holiday(timestamp: datetime) -> bool:
    if timestamp.month in {7, 8}:
        return True
    if timestamp.month == 3 and 24 <= timestamp.day <= 31:
        return True
    return False


def gaussian_peak(value: float, center: float, width: float) -> float:
    return math.exp(-((value - center) ** 2) / (2.0 * width * width))


def clamp(value: float, lower: float, upper: float) -> float:
    return max(lower, min(upper, value))


if __name__ == "__main__":
    main()
