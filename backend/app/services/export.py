"""Transcript / summary export to TXT, Markdown, DOCX and PDF."""

import io
import re
from typing import Any


def _segments_text(recording: Any, transcript: Any, segments: list[Any]) -> str:
    if transcript and transcript.refinedText:
        return transcript.refinedText
    if segments:
        lines = []
        for s in segments:
            ts = f"[{_fmt_ms(s.startMs)}]"
            speaker = f" {s.speaker}:" if s.speaker else ""
            lines.append(f"{ts}{speaker} {s.editedText or s.text}")
        return "\n".join(lines)
    if transcript and transcript.rawText:
        return transcript.rawText
    return ""


def _fmt_ms(ms: int) -> str:
    total = int(ms / 1000)
    h, rem = divmod(total, 3600)
    m, s = divmod(rem, 60)
    return f"{h:02d}:{m:02d}:{s:02d}" if h else f"{m:02d}:{s:02d}"


def build_txt(recording: Any, transcript: Any, segments: list[Any]) -> bytes:
    title = recording.title or "Recording"
    body = _segments_text(recording, transcript, segments)
    return f"{title}\n{'=' * len(title)}\n\n{body}\n".encode("utf-8")


def build_markdown(recording: Any, transcript: Any, segments: list[Any]) -> bytes:
    title = recording.title or "Recording"
    body = _segments_text(recording, transcript, segments)
    return f"# {title}\n\n{body}\n".encode("utf-8")


def build_docx(recording: Any, transcript: Any, segments: list[Any]) -> bytes:
    from docx import Document

    doc = Document()
    doc.add_heading(recording.title or "Recording", level=1)
    if transcript and transcript.refinedText:
        for para in transcript.refinedText.split("\n"):
            clean = re.sub(r"\*\*(.+?)\*\*", r"\1", para)
            doc.add_paragraph(clean)
    elif segments:
        for s in segments:
            p = doc.add_paragraph()
            if s.speaker:
                run = p.add_run(f"{s.speaker} ")
                run.bold = True
            p.add_run(f"[{_fmt_ms(s.startMs)}] {s.editedText or s.text}")
    elif transcript and transcript.rawText:
        doc.add_paragraph(transcript.rawText)
    buf = io.BytesIO()
    doc.save(buf)
    return buf.getvalue()


def build_pdf(recording: Any, transcript: Any, segments: list[Any]) -> bytes:
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import getSampleStyleSheet
    from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer

    buf = io.BytesIO()
    doc = SimpleDocTemplate(buf, pagesize=A4)
    styles = getSampleStyleSheet()
    flow: list[Any] = [Paragraph(_escape(recording.title or "Recording"), styles["Title"]), Spacer(1, 12)]
    body = _segments_text(recording, transcript, segments)
    for para in body.split("\n"):
        if para.strip():
            flow.append(Paragraph(_escape(para), styles["BodyText"]))
            flow.append(Spacer(1, 6))
    doc.build(flow)
    return buf.getvalue()


def _escape(text: str) -> str:
    return (
        text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("**", "")
    )


EXPORTERS = {
    "txt": ("text/plain", build_txt),
    "md": ("text/markdown", build_markdown),
    "docx": ("application/vnd.openxmlformats-officedocument.wordprocessingml.document", build_docx),
    "pdf": ("application/pdf", build_pdf),
}
