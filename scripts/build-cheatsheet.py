#!/usr/bin/env python3
"""
build-cheatsheet.py — render the SINGLE source of truth (content/topics/*.md banks)
into one searchable static HTML lookup page (cheatsheet.html).

The question banks are canonical. This script only READS them. Run it after editing
any bank to regenerate the lookup page:

    python3 scripts/build-cheatsheet.py

No third-party deps — stdlib only (Python 3.8+). Includes a minimal Markdown renderer
sufficient for the bank content (paragraphs, fenced code, inline code, bold, lists, tables).
"""

import html
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOPICS_DIR = ROOT / "content" / "topics"
TOPICS_JSON = ROOT / "state" / "topics.json"
OUT = ROOT / "cheatsheet.html"

CLUSTER_LABELS = {
    "java_core": "Java Core",
    "spring": "Spring",
    "data": "Data",
    "distributed": "Distributed / Architecture",
    "cloud_ops": "Cloud / Ops",
    "kotlin_allegro": "Kotlin / Allegro",
    "behavioral": "Behavioral",
}

LEVEL_ORDER = {"junior": 0, "regular": 1, "senior": 2, "master": 3}

# ----------------------------------------------------------------------------- markdown
def md_inline(text):
    """Inline markdown -> HTML. Operates on already-escaped text segments carefully."""
    # protect inline code first
    codes = []
    def stash(m):
        codes.append(m.group(1))
        return f"\x00{len(codes)-1}\x00"
    text = re.sub(r"`([^`]+)`", stash, text)
    text = html.escape(text, quote=False)
    # bold then italic
    text = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"(?<!\*)\*([^*]+)\*(?!\*)", r"<em>\1</em>", text)
    # links [t](u)
    text = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2" target="_blank" rel="noopener">\1</a>', text)
    # restore code
    def unstash(m):
        return "<code>" + html.escape(codes[int(m.group(1))], quote=False) + "</code>"
    text = re.sub(r"\x00(\d+)\x00", unstash, text)
    return text


def render_markdown(md):
    """Block-level markdown -> HTML. Handles fenced code, tables, lists, paragraphs."""
    lines = md.split("\n")
    out = []
    i = 0
    n = len(lines)
    while i < n:
        line = lines[i]

        # fenced code block
        m = re.match(r"^```(\w*)\s*$", line)
        if m:
            lang = m.group(1)
            i += 1
            buf = []
            while i < n and not re.match(r"^```\s*$", lines[i]):
                buf.append(lines[i])
                i += 1
            i += 1  # skip closing fence
            code = html.escape("\n".join(buf), quote=False)
            cls = f' class="lang-{lang}"' if lang else ""
            out.append(f"<pre><code{cls}>{code}</code></pre>")
            continue

        # table: a line with | and the next line a separator of ---
        if "|" in line and i + 1 < n and re.match(r"^\s*\|?[\s:|-]+\|[\s:|-]+$", lines[i + 1]):
            header = [c.strip() for c in line.strip().strip("|").split("|")]
            i += 2
            rows = []
            while i < n and "|" in lines[i] and lines[i].strip():
                rows.append([c.strip() for c in lines[i].strip().strip("|").split("|")])
                i += 1
            th = "".join(f"<th>{md_inline(c)}</th>" for c in header)
            trs = "".join("<tr>" + "".join(f"<td>{md_inline(c)}</td>" for c in r) + "</tr>" for r in rows)
            out.append(f"<table><thead><tr>{th}</tr></thead><tbody>{trs}</tbody></table>")
            continue

        # unordered list
        if re.match(r"^\s*[-*]\s+", line):
            items = []
            while i < n and re.match(r"^\s*[-*]\s+", lines[i]):
                items.append(re.sub(r"^\s*[-*]\s+", "", lines[i]))
                i += 1
            out.append("<ul>" + "".join(f"<li>{md_inline(it)}</li>" for it in items) + "</ul>")
            continue

        # ordered list
        if re.match(r"^\s*\d+\.\s+", line):
            items = []
            while i < n and re.match(r"^\s*\d+\.\s+", lines[i]):
                items.append(re.sub(r"^\s*\d+\.\s+", "", lines[i]))
                i += 1
            out.append("<ol>" + "".join(f"<li>{md_inline(it)}</li>" for it in items) + "</ol>")
            continue

        # blank
        if not line.strip():
            i += 1
            continue

        # paragraph (gather until blank / block start)
        buf = [line]
        i += 1
        while i < n and lines[i].strip() and not re.match(r"^(```|\s*[-*]\s+|\s*\d+\.\s+)", lines[i]) \
                and not ("|" in lines[i] and i + 1 < n and re.match(r"^\s*\|?[\s:|-]+\|[\s:|-]+$", lines[i + 1])):
            buf.append(lines[i])
            i += 1
        out.append("<p>" + md_inline(" ".join(buf)) + "</p>")

    return "\n".join(out)


# ----------------------------------------------------------------------------- parsing
Q_HEADER = re.compile(
    r"^##\s+(Q-[A-Z0-9]+-\d+)\s*(?:\[bloom:\s*(\w+)\])?\s*(?:\[level:\s*(\w+)\])?",
    re.IGNORECASE,
)
FIELD = re.compile(r"^\*\*(Question|Model answer|Interview trap|Tags):\*\*\s*(.*)$")


def parse_bank(path):
    text = path.read_text(encoding="utf-8")
    title = path.stem
    context = ""
    scope = []
    questions = []

    m = re.search(r"^#\s+(.+)$", text, re.MULTILINE)
    if m:
        title = m.group(1).strip()
        # strip trailing "— question bank" / "— bank pytań"
        title = re.sub(r"\s*[—-]\s*(question bank|bank pyta.*)$", "", title, flags=re.IGNORECASE).strip()

    m = re.search(r"^>\s?(.+(?:\n>.*)*)", text, re.MULTILINE)
    if m:
        context = re.sub(r"\n>\s?", " ", m.group(1)).strip()

    sm = re.search(r"^##\s+Scope\s*\n(.+?)(?=\n---|\n##\s+Q-)", text, re.MULTILINE | re.DOTALL)
    if sm:
        scope = [re.sub(r"^\s*[-*]\s+", "", l).strip()
                 for l in sm.group(1).splitlines() if l.strip().startswith(("-", "*"))]

    # split into question blocks on the Q header
    parts = re.split(r"(?=^##\s+Q-)", text, flags=re.MULTILINE)
    for part in parts:
        hm = Q_HEADER.match(part)
        if not hm:
            continue
        qid, bloom, level = hm.group(1), (hm.group(2) or "").lower(), (hm.group(3) or "").lower()
        body = part[part.index("\n") + 1:] if "\n" in part else ""
        fields = {"Question": "", "Model answer": "", "Interview trap": "", "Tags": ""}
        current = None
        buf = []
        for line in body.splitlines():
            if line.strip() == "---":
                break
            fm = FIELD.match(line)
            if fm:
                if current:
                    fields[current] = "\n".join(buf).strip()
                current = fm.group(1)
                buf = [fm.group(2)]
            elif current:
                buf.append(line)
        if current:
            fields[current] = "\n".join(buf).strip()

        tags = [t.strip() for t in fields["Tags"].split(",") if t.strip()]
        questions.append({
            "id": qid, "bloom": bloom, "level": level or "regular",
            "question": fields["Question"], "answer": fields["Model answer"],
            "trap": fields["Interview trap"], "tags": tags,
        })
    return {"title": title, "context": context, "scope": scope, "questions": questions}


# ----------------------------------------------------------------------------- html
LEVEL_BADGE = {
    "junior": ("JR", "#7ee787"), "regular": ("REG", "#4cc2ff"),
    "senior": ("SR", "#ffb454"), "master": ("MASTER", "#ff7b72"),
}


def render_question(q):
    lvl = q["level"]
    badge_txt, badge_col = LEVEL_BADGE.get(lvl, ("REG", "#4cc2ff"))
    answer_html = render_markdown(q["answer"])
    trap_html = ""
    if q["trap"] and q["trap"].strip() not in ("", "—", "-"):
        trap_html = f'<div class="trap"><span class="trap-label">Trap</span> {md_inline(q["trap"])}</div>'
    tags_html = "".join(f'<span class="tag">{html.escape(t)}</span>' for t in q["tags"])
    search_blob = html.escape(" ".join([q["question"], q["answer"], q["trap"], " ".join(q["tags"]), q["id"]]).lower(), quote=True)
    return f'''<details class="q" data-level="{lvl}" data-search="{search_blob}">
  <summary>
    <span class="lvl" style="--c:{badge_col}">{badge_txt}</span>
    <span class="qid">{q["id"]}</span>
    <span class="qtext">{md_inline(q["question"])}</span>
  </summary>
  <div class="ans">{answer_html}{trap_html}
    <div class="tags">{tags_html}</div>
  </div>
</details>'''


def build():
    meta = json.loads(TOPICS_JSON.read_text())["topics"]
    # order: cluster, then study_track order within cluster
    track = json.loads(TOPICS_JSON.read_text()).get("study_track", [])
    order = {t: i for i, t in enumerate(track)}

    topics = []
    for tid, info in meta.items():
        path = TOPICS_DIR / f"{tid}.md"
        if not path.exists():
            continue
        bank = parse_bank(path)
        if not bank["questions"]:
            continue
        topics.append((tid, info, bank))

    topics.sort(key=lambda x: order.get(x[0], 999))

    # group by cluster preserving order
    clusters = {}
    for tid, info, bank in topics:
        clusters.setdefault(info.get("cluster", "other"), []).append((tid, info, bank))

    total_q = sum(len(b["questions"]) for _, _, b in topics)

    nav, sections = [], []
    for cluster, items in clusters.items():
        label = CLUSTER_LABELS.get(cluster, cluster.title())
        nav.append(f'<div class="nav-group">{html.escape(label)}</div>')
        for tid, info, bank in items:
            qs = sorted(bank["questions"], key=lambda q: (LEVEL_ORDER.get(q["level"], 1), q["id"]))
            counts = {}
            for q in qs:
                counts[q["level"]] = counts.get(q["level"], 0) + 1
            count_str = " · ".join(f"{LEVEL_BADGE[l][0]} {counts[l]}" for l in ["junior","regular","senior","master"] if l in counts)
            nav.append(f'<a href="#{tid}">{html.escape(bank["title"])} <span class="nq">{len(qs)}</span></a>')
            scope_html = ""
            if bank["scope"]:
                scope_html = "<ul class=\"scope\">" + "".join(f"<li>{md_inline(s)}</li>" for s in bank["scope"]) + "</ul>"
            qhtml = "\n".join(render_question(q) for q in qs)
            sections.append(f'''<section class="topic" id="{tid}" data-topic="{tid}">
  <h2>{html.escape(bank["title"])} <span class="prio prio-{info.get('priority','normal')}">{info.get('priority','')}</span></h2>
  <p class="ctx">{md_inline(bank["context"])}</p>
  <div class="counts">{count_str} &nbsp;·&nbsp; {len(qs)} questions</div>
  <details class="scope-wrap"><summary>Scope</summary>{scope_html}</details>
  {qhtml}
</section>''')

    nav_html = "\n".join(nav)
    sections_html = "\n".join(sections)
    n_topics = len(topics)

    return PAGE.replace("__NAV__", nav_html).replace("__SECTIONS__", sections_html) \
               .replace("__TOTALQ__", str(total_q)).replace("__NTOPICS__", str(n_topics))


PAGE = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Major Fucker — Senior Backend Knowledge Base</title>
<style>
  :root{
    --bg:#0e1116;--bg-card:#161b22;--bg-hover:#1f2730;--border:#2a3340;
    --text:#e6edf3;--text-dim:#9aa6b2;--accent:#4cc2ff;--accent-2:#ffb454;
    --warn:#ff7b72;--ok:#7ee787;
    --mono:'JetBrains Mono','SF Mono',Menlo,Consolas,monospace;
    --sans:-apple-system,BlinkMacSystemFont,'Segoe UI',Inter,system-ui,sans-serif;
  }
  *{box-sizing:border-box}
  html,body{margin:0;padding:0;background:var(--bg);color:var(--text);font-family:var(--sans);line-height:1.55;font-size:15px}
  body{display:grid;grid-template-columns:280px 1fr;min-height:100vh}
  aside{position:sticky;top:0;height:100vh;overflow-y:auto;background:#0a0d12;border-right:1px solid var(--border);padding:18px 14px}
  aside h1{font-size:14px;margin:0 0 2px;color:var(--accent);letter-spacing:.5px;text-transform:uppercase}
  aside .sub{font-size:11px;color:var(--text-dim);margin-bottom:14px}
  .nav-group{font-size:10px;text-transform:uppercase;letter-spacing:.6px;color:var(--accent-2);margin:14px 0 4px;padding-left:8px}
  aside nav a,aside a{display:flex;justify-content:space-between;align-items:center;gap:6px;padding:5px 10px;border-radius:6px;color:var(--text-dim);text-decoration:none;font-size:12.5px;border-left:2px solid transparent}
  aside a:hover{background:var(--bg-hover);color:var(--text)}
  aside a.active{color:var(--accent);border-left-color:var(--accent);background:var(--bg-hover)}
  .nq{font-size:10px;color:var(--text-dim);background:var(--bg-card);border-radius:8px;padding:1px 6px;font-family:var(--mono)}
  main{padding:24px 38px 100px;max-width:1080px}
  header.page{position:sticky;top:0;z-index:10;background:linear-gradient(180deg,var(--bg) 82%,transparent);padding:8px 0 14px;margin-bottom:18px;border-bottom:1px solid var(--border)}
  header.page h1{margin:0 0 4px;font-size:22px;font-weight:600}
  header.page p{margin:0 0 12px;color:var(--text-dim);font-size:13px}
  #search{width:100%;padding:12px 16px;background:var(--bg-card);color:var(--text);border:1px solid var(--border);border-radius:8px;font-size:15px;font-family:var(--sans);outline:none;transition:border-color .15s}
  #search:focus{border-color:var(--accent)}
  .filters{display:flex;gap:6px;margin-top:10px;flex-wrap:wrap}
  .filters button{background:var(--bg-card);color:var(--text-dim);border:1px solid var(--border);border-radius:20px;padding:4px 12px;font-size:12px;cursor:pointer;font-family:var(--sans)}
  .filters button.on{color:#0e1116;border-color:transparent}
  .filters button[data-lvl=junior].on{background:var(--ok)}
  .filters button[data-lvl=regular].on{background:var(--accent)}
  .filters button[data-lvl=senior].on{background:var(--accent-2)}
  .filters button[data-lvl=master].on{background:var(--warn)}
  .stats{font-size:12px;color:var(--text-dim);margin-top:8px}
  section.topic{margin-bottom:34px;scroll-margin-top:140px}
  section.topic h2{font-size:19px;margin:0 0 4px;display:flex;align-items:center;gap:10px;border-bottom:1px solid var(--border);padding-bottom:6px}
  .prio{font-size:9px;text-transform:uppercase;letter-spacing:.5px;padding:2px 7px;border-radius:10px;font-weight:600}
  .prio-critical{background:rgba(255,123,114,.18);color:var(--warn)}
  .prio-high{background:rgba(255,180,84,.16);color:var(--accent-2)}
  .prio-normal{background:rgba(76,194,255,.14);color:var(--accent)}
  .prio-low{background:rgba(154,166,178,.14);color:var(--text-dim)}
  .ctx{color:var(--text-dim);font-size:13px;margin:6px 0}
  .counts{font-size:11px;color:var(--text-dim);font-family:var(--mono);margin-bottom:8px}
  .scope-wrap{margin-bottom:12px}
  .scope-wrap summary{cursor:pointer;color:var(--accent-2);font-size:12px}
  ul.scope{columns:2;font-size:12.5px;color:var(--text-dim);margin:8px 0}
  details.q{background:var(--bg-card);border:1px solid var(--border);border-radius:8px;margin-bottom:8px;overflow:hidden}
  details.q[open]{border-color:var(--accent)}
  details.q summary{cursor:pointer;padding:11px 14px;list-style:none;display:flex;align-items:flex-start;gap:10px;font-size:14.5px}
  details.q summary::-webkit-details-marker{display:none}
  .lvl{flex:none;font-size:9px;font-weight:700;letter-spacing:.5px;color:#0e1116;background:var(--c);border-radius:4px;padding:2px 6px;margin-top:2px}
  .qid{flex:none;font-family:var(--mono);font-size:10px;color:var(--text-dim);margin-top:3px}
  .qtext{flex:1;font-weight:500}
  .ans{padding:2px 16px 14px 16px;border-top:1px solid var(--border);font-size:14px}
  .ans p{margin:10px 0}
  .ans code{font-family:var(--mono);font-size:12.5px;background:#0a0d12;padding:1px 5px;border-radius:4px;color:var(--accent-2)}
  .ans pre{background:#0a0d12;border:1px solid var(--border);border-radius:6px;padding:12px;overflow-x:auto}
  .ans pre code{background:none;color:var(--text);padding:0;font-size:12.5px}
  .ans table{border-collapse:collapse;margin:12px 0;font-size:13px;width:100%}
  .ans th,.ans td{border:1px solid var(--border);padding:6px 10px;text-align:left}
  .ans th{background:var(--bg-hover);color:var(--accent)}
  .ans ul,.ans ol{margin:10px 0;padding-left:22px}
  .ans li{margin:3px 0}
  .trap{background:rgba(255,123,114,.1);border-left:3px solid var(--warn);padding:8px 12px;margin:12px 0;border-radius:0 6px 6px 0;font-size:13px}
  .trap-label{color:var(--warn);font-weight:700;font-size:10px;text-transform:uppercase;letter-spacing:.5px;margin-right:6px}
  .tags{margin-top:10px;display:flex;gap:5px;flex-wrap:wrap}
  .tag{font-size:10px;color:var(--text-dim);background:var(--bg-hover);border-radius:4px;padding:2px 7px;font-family:var(--mono)}
  .hidden{display:none!important}
  mark{background:var(--accent-2);color:#0e1116;border-radius:2px}
</style>
</head>
<body>
<aside>
  <h1>Major Fucker KB</h1>
  <div class="sub">Single source of truth · generated from content/topics/*.md</div>
  <nav id="toc">
__NAV__
  </nav>
</aside>
<main>
  <header class="page">
    <h1>Senior Backend Knowledge Base</h1>
    <p>__NTOPICS__ topics · __TOTALQ__ questions · Java · Spring · Microservices · Data · Cloud · Kotlin</p>
    <input type="text" id="search" placeholder="Search (e.g. 'treeify', 'saga', 'actuator health', 'isolation level')..." autofocus>
    <div class="filters">
      <button data-lvl="junior" class="on">Junior</button>
      <button data-lvl="regular" class="on">Regular</button>
      <button data-lvl="senior" class="on">Senior</button>
      <button data-lvl="master" class="on">Master</button>
    </div>
    <div class="stats" id="stats"></div>
  </header>
__SECTIONS__
</main>
<script>
const search=document.getElementById('search');
const stats=document.getElementById('stats');
const qs=[...document.querySelectorAll('details.q')];
const active=new Set(['junior','regular','senior','master']);
function esc(s){return s.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');}
function apply(){
  const term=search.value.trim().toLowerCase();
  let shown=0;
  qs.forEach(d=>{
    const lvlOk=active.has(d.dataset.level);
    const txtOk=!term||d.dataset.search.includes(term);
    const ok=lvlOk&&txtOk;
    d.classList.toggle('hidden',!ok);
    if(ok)shown++;
    if(term&&ok)d.open=true; else if(!term)d.open=false;
  });
  document.querySelectorAll('section.topic').forEach(s=>{
    const any=[...s.querySelectorAll('details.q')].some(d=>!d.classList.contains('hidden'));
    s.classList.toggle('hidden',!any);
  });
  stats.textContent=shown+' / '+qs.length+' questions'+(term?' matching "'+term+'"':'');
}
search.addEventListener('input',apply);
document.querySelectorAll('.filters button').forEach(b=>{
  b.addEventListener('click',()=>{
    const l=b.dataset.lvl;
    if(active.has(l)){active.delete(l);b.classList.remove('on');}
    else{active.add(l);b.classList.add('on');}
    apply();
  });
});
// scroll spy
const links=[...document.querySelectorAll('#toc a')];
const obs=new IntersectionObserver(es=>{es.forEach(e=>{if(e.isIntersecting){links.forEach(l=>l.classList.toggle('active',l.getAttribute('href')==='#'+e.target.id));}});},{rootMargin:'-40% 0px -55% 0px'});
document.querySelectorAll('section.topic').forEach(s=>obs.observe(s));
apply();
</script>
</body>
</html>"""


if __name__ == "__main__":
    OUT.write_text(build(), encoding="utf-8")
    print(f"Wrote {OUT.relative_to(ROOT)}")
