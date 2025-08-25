#!/usr/bin/env python3
import generate
import urllib.request
import gzip
from pathlib import Path

TRANSLATION_BASE_URL = "https://media.githubusercontent.com/media/mozilla/firefox-translations-models/39a47f355e808057bbda8ee1556a546a4c6e0558/models"
TESSERACT_BASE_URL = "https://github.com/tesseract-ocr/tessdata_fast/raw/refs/heads/main"

def download(url: str, output_path: Path, decompress: bool):
    print(f"Downloading {url}")
    with urllib.request.urlopen(url) as response:
        data = response.read()
        if decompress:
            data = gzip.decompress(data)

    with open(output_path, 'wb') as f_out:
        f_out.write(data)

def download_language_pair(src_lang, tgt_lang, model_type="base", output_dir="models"):
    output_dir = Path(output_dir)
    bin_dir = output_dir / "bin"
    tessdata_dir = output_dir / "tesseract" / "tessdata"

    bin_dir.mkdir(parents=True, exist_ok=True)
    tessdata_dir.mkdir(parents=True, exist_ok=True)

    print(f"\n=== Downloading {src_lang} -> {tgt_lang} ({model_type}) ===")
    en_to_other_files = generate.generate_files_for_language(src_lang, tgt_lang)
    for filename in sorted(set(en_to_other_files.values())):
        url = f"{TRANSLATION_BASE_URL}/{model_type}/{src_lang}{tgt_lang}/{filename}.gz"
        output_path = bin_dir / filename
        download(url, output_path, decompress=True)

    lang_code = src_lang if src_lang != "en" else tgt_lang
    print(f"\n=== Downloading Tesseract OCR for {lang_code.upper()} ===")
    tess_name = generate.TESSERACT_LANGUAGE_MAPPINGS[lang_code]
    tess_filename = f"{tess_name}.traineddata"
    tess_url = f"{TESSERACT_BASE_URL}/{tess_filename}"
    tess_output_path = tessdata_dir / tess_filename
    if not tess_output_path.exists():  # don't want to download both times
      download(tess_url, tess_output_path, decompress=False)

if __name__ == "__main__":
    download_language_pair("en", "es", "tiny", "translator_models")
    download_language_pair("es", "en", "tiny", "translator_models")

    # dutch has base-memory for nl->en, which is much better than tiny
    # but only tiny for en->nl
