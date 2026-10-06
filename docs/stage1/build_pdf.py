#!/usr/bin/env python3
"""Builds Stage1-Design-Report.pdf from Stage1-Design-Report.md with headless Google Chrome.

Wide UMLet diagrams are placed on landscape pages; the embedded PNGs keep their full resolution, so
they can be zoomed in the PDF. Requires the `markdown` and `Pillow` packages.
Usage: python3 build_pdf.py
"""
import pathlib
import re
import subprocess

import markdown
from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
CSS = """@page { size: A4; margin: 14mm; }
@page wide { size: A4 landscape; margin: 10mm; }
body{font-family:-apple-system,Helvetica,Arial;font-size:10pt;line-height:1.35}
h1{page-break-before:always;font-size:18pt} h1:first-of-type{page-break-before:avoid}
h2{font-size:14pt;margin-top:18pt} h3{font-size:12pt} h4{font-size:11pt}
table{border-collapse:collapse;width:100%;font-size:8.5pt;margin:6pt 0}
td,th{border:1px solid #999;padding:3px 5px;vertical-align:top}
img{max-width:100%;page-break-inside:avoid;display:block;margin:6pt auto}
.wide{page:wide;page-break-before:always}
.wide-text{page:wide}
.wide-text table{font-size:8pt}
.wide img{max-height:150mm;object-fit:contain}
.wide p{margin:4pt 0}
pre{background:#f4f4f4;padding:6px;font-size:8pt;white-space:pre-wrap}
code{font-size:8.5pt}"""


def wrap_wide(html: str) -> str:
    """Puts each diagram wider than 1.3x its height on its own landscape page, with its heading."""
    def repl(m):
        heading, img, src, text = m.group(1), m.group(3), m.group(4), m.group(5) or ""
        w, h = Image.open(HERE / src).size
        return f'<div class="wide">{heading}{img}{text}</div>' if w > 1.3 * h else m.group(0)
    # heading, image, and the description paragraph that follows the image (if any)
    return re.sub(r'(<h([34])[^>]*>[^<]*</h\2>\s*)<p>(<img[^>]*src="(umlet/[^"]+\.png)"[^>]*>)</p>'
                  r'(\s*<p>(?!<img)(?s:(?:(?!</p>).)*)</p>)?', repl, html)


def wrap_task3(html: str) -> str:
    """Puts the eight-column traceability table (Task 3) on landscape pages."""
    i = html.index('<h1 id="task-3')
    j = html.index('<h1 id="task-4')
    return html[:i] + '<div class="wide-text">' + html[i:j] + "</div>" + html[j:]


def main():
    md = (HERE / "Stage1-Design-Report.md").read_text()
    body = markdown.markdown(md, extensions=["tables", "toc", "sane_lists"], tab_length=2)
    body = re.sub(r'<hr />\s*(?=<h1)', '', body)  # h1 already starts a new page
    html = (f"<html><head><meta charset='utf-8'><base href='{HERE.as_uri()}/'>"
            f"<style>{CSS}</style></head><body>{wrap_task3(wrap_wide(body))}</body></html>")
    out_html = HERE / ".report-build.html"
    out_html.write_text(html)
    subprocess.run([CHROME, "--headless", "--no-pdf-header-footer", "--allow-file-access-from-files",
                    f"--print-to-pdf={HERE / 'Stage1-Design-Report.pdf'}", out_html.as_uri()],
                   check=True, capture_output=True)
    out_html.unlink()
    print("wrote", HERE / "Stage1-Design-Report.pdf")


if __name__ == "__main__":
    main()
