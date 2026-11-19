from __future__ import annotations

import argparse
import sys
from datetime import UTC, datetime
from pathlib import Path

from catalog_probe.contributions import default_contributions_dir, load_contributed_cameras
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
        description="Validate & probe world camera endpoints and write catalog.v5.json manifests.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        action="append",
        help="Destination path(s) for catalog.v5.json (may be specified multiple times).",
    )
    parser.add_argument(
        "--contributions-dir",
        type=Path,
        default=None,
        help="Optional directory for community drop-in JSON files (defaults to catalog/d).",
    )
    parser.add_argument(
        "--check-contributions",
        action="store_true",
        help=(
            "Validate catalog + community catalog/d/*.json files "
            "(and probe contributed streams unless --offline)."
        ),
    )
    parser.add_argument(
        "--offline",
        action="store_true",
        help="Validate or write curated catalog without executing live HTTP probes.",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=6.0,
        help="Per-request timeout in seconds for live HTTP probes.",
    )
    parser.add_argument(
        "--report-unreachable",
        action="store_true",
        help="Log unreachable stream endpoints and failure reasons to stderr.",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if not args.output and not args.check_contributions:
        print(
            "Error: specify at least --output <path> or --check-contributions.",
            file=sys.stderr,
        )
        return 2

    cameras = build_curated_cameras(contributions_dir=args.contributions_dir)

    if args.check_contributions and not args.output:
        contrib_dir = args.contributions_dir or default_contributions_dir()
        contributed = load_contributed_cameras(contrib_dir)
        print(
            f"Validated {len(cameras)} total cameras ({len(contributed)} from {contrib_dir}).",
            file=sys.stderr,
        )
        if contributed and not args.offline:
            results = probe_cameras_live(contributed, timeout_seconds=args.timeout)
            unreachable = [
                f"  - {cam.id} ({stream.type.value}): {stream.url}"
                for cam in contributed
                for stream in cam.streams
                if not results.get(stream.url) or not results[stream.url].reachable
            ]
            if unreachable:
                print(
                    "Contributed stream reachability check failed on endpoints:\n"
                    + "\n".join(unreachable),
                    file=sys.stderr,
                )
                return 1
            print(
                f"All {len(results)} contributed stream endpoint(s) responded healthy.",
                file=sys.stderr,
            )
        return 0

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
        if args.report_unreachable:
            for cam in cameras:
                for stream in cam.streams:
                    res = results.get(stream.url)
                    if res is not None and not res.reachable:
                        detail = res.error or f"HTTP {res.status_code}"
                        print(
                            f"UNREACHABLE [{cam.city.value}] {cam.id} "
                            f"({stream.type.value}): {stream.url} -> {detail}",
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

    for target in args.output or []:
        target.parent.mkdir(parents=True, exist_ok=True)
        tmp_path = target.with_suffix(target.suffix + ".tmp")
        tmp_path.write_text(rendered, encoding="utf-8")
        tmp_path.replace(target)
        print(f"Wrote {len(cameras)} cameras -> {target}", file=sys.stderr)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
