#!/usr/bin/env python3
"""
Render Markdown to a styled PDF using Python-Markdown + WeasyPrint.

Usage:
  .venv/bin/python scripts/render_md_to_pdf.py input.md output.pdf
"""

from __future__ import annotations

import html
import sys
from pathlib import Path

import markdown
from weasyprint import HTML


CSS = """
@page {
  size: A4;
  margin: 1.8cm;
}
body {
  font-family: "DejaVu Serif", Georgia, serif;
  color: #0f172a;
  line-height: 1.38;
  font-size: 11pt;
}
h1, h2, h3, h4 {
  color: #0b3b52;
  margin-top: 1.1em;
  margin-bottom: 0.4em;
  line-height: 1.2;
  page-break-after: avoid;
}
h1 { font-size: 20pt; border-bottom: 2px solid #0b3b52; padding-bottom: 6px; }
h2 { font-size: 15pt; border-bottom: 1px solid #94a3b8; padding-bottom: 3px; }
h3 { font-size: 12.5pt; }
h4 { font-size: 11.5pt; }
p, li, td, th { orphans: 3; widows: 3; }
code {
  font-family: "DejaVu Sans Mono", Consolas, monospace;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 9.5pt;
}
pre {
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-left: 4px solid #0b3b52;
  padding: 8px;
  overflow: auto;
  page-break-inside: avoid;
  font-size: 9pt;
}
table {
  width: 100%;
  border-collapse: collapse;
  margin: 10px 0 14px 0;
  page-break-inside: avoid;
}
th, td {
  border: 1px solid #cbd5e1;
  padding: 6px 7px;
  vertical-align: top;
  text-align: left;
}
th {
  background: #e2e8f0;
  color: #0b3b52;
  font-weight: 700;
}
blockquote {
  margin: 8px 0;
  border-left: 4px solid #94a3b8;
  background: #f8fafc;
  padding: 6px 10px;
}
ul, ol {
  margin-top: 0.35em;
  margin-bottom: 0.45em;
}
hr {
  border: none;
  border-top: 1px solid #cbd5e1;
  margin: 12px 0;
}
"""


def wrap_html(title: str, body_html: str) -> str:
    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <title>{html.escape(title)}</title>
  <style>
{CSS}
  </style>
</head>
<body>
{body_html}
</body>
</html>
"""


def main() -> int:
    if len(sys.argv) != 3:
        print("Usage: render_md_to_pdf.py <input.md> <output.pdf>")
        return 2

    input_path = Path(sys.argv[1]).resolve()
    output_path = Path(sys.argv[2]).resolve()
    if not input_path.is_file():
        print(f"Input not found: {input_path}")
        return 2

    md_text = input_path.read_text(encoding="utf-8")
    body_html = markdown.markdown(
        md_text,
        extensions=[
            "extra",
            "tables",
            "fenced_code",
            "sane_lists",
            "toc",
        ],
        output_format="html5",
    )
    title = input_path.stem.replace("_", " ")
    html_doc = wrap_html(title, body_html)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    HTML(string=html_doc, base_url=str(input_path.parent)).write_pdf(str(output_path))
    print(f"Rendered {input_path} -> {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

