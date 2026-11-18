from __future__ import annotations

import argparse
import sys
from datetime import UTC, datetime
from pathlib import Path

from catalog_probe.curated import build_curated_cameras
from catalog_probe.models import CatalogManifest, manifest_from_json, manifest_to_json
from catalog_probe.prober import (
    apply_probe_ordering,
    probe_cameras_live,
    refresh_rostelecom_live_urls,
    refresh_rutube_live_urls,
)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="catalog-probe",
        description="Probe world camera endpoints and write static catalog.v5.json manifests.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        action="append",
        required=True,
        help="Destination path(s) for catalog.v5.json (may be specified multiple times).",
    )
    parser.add_argument(
        "--offline",
        action="store_true",
        help="Write curated catalog without executing live HTTP probes.",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=6.0,
        help="Per-request timeout in seconds for live HTTP probes.",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    cameras = build_curated_cameras()

    if not args.offline:
        cameras = refresh_rutube_live_urls(cameras, timeout_seconds=args.timeout)
        cameras = refresh_rostelecom_live_urls(cameras, timeout_seconds=args.timeout)
        results = probe_cameras_live(cameras, timeout_seconds=args.timeout)
        reachable_count = sum(1 for item in results.values() if item.reachable)
        total_count = len(results)
        print(
            f"Probed {total_count} unique endpoints: {reachable_count} reachable.",
            file=sys.stderr,
        )
        if reachable_count == 0:
            print("Aborting update: zero endpoints reachable.", file=sys.stderr)
            return 1
        cameras = [apply_probe_ordering(cam, results) for cam in cameras]

    now = datetime.now(tz=UTC)
    revision = int(now.strftime("%Y%m%d%H"))
    updated_at = now.strftime("%Y-%m-%dT%H:%M:%SZ")

    manifest = CatalogManifest(
        schema_version=5,
        revision=revision,
        updated_at=updated_at,
        cameras=tuple(cameras),
    )
    rendered = manifest_to_json(manifest)
    # Enforce round-trip fixed point prior to touching disk
    if manifest_to_json(manifest_from_json(rendered)) != rendered:
        print("Serialization fixed-point invariant failed.", file=sys.stderr)
        return 2

    for target in args.output:
        target.parent.mkdir(parents=True, exist_ok=True)
        tmp_path = target.with_suffix(target.suffix + ".tmp")
        tmp_path.write_text(rendered, encoding="utf-8")
        tmp_path.replace(target)
        print(f"Wrote {len(cameras)} cameras -> {target}", file=sys.stderr)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
