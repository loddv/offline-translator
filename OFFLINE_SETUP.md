# Offline language files setup

This guide explains how to pre-download translation and OCR model files on your computer and transfer them to your Android device for offline use with the Translator app.

You only need this guide if you want to download files on another device and then copy them to where you intend to use the translator.

On the app, you will need to enable in: Settings &rarr; Advanced &rarr; "Use external storage"

The downloaded files should be placed in `Documents/dev.davidv.translator/`, uncompressed

You **must** download files to translate bidirectionally (lang &harr; English), or the language won't show up as available.

## Directory Structure

```
dev.davidv.translator/
├── bin/                          # Translation model files
│   ├── model.enar.intgemm.alphas.bin
│   ├── vocab.enar.spm
│   ├── lex.50.50.enar.s2t.bin
│   └── ... (other language pairs)
└── tesseract/
    └── tessdata/                 # OCR model files
        ├── eng.traineddata
        ├── ara.traineddata
        └── ... (other languages)
```


## How to get the files

Base URLs come from `app/src/main/java/dev/davidv/translator/Constants.kt`, and look like this (may be outdated):

- **Translation models**: `https://media.githubusercontent.com/media/mozilla/firefox-translations-models/39a47f355e808057bbda8ee1556a546a4c6e0558/models`
- **Tesseract**: `https://github.com/tesseract-ocr/tessdata_fast/raw/refs/heads/main`

For the latest model commit hash, check the [Firefox Translations Models repository](https://github.com/mozilla/firefox-translations-models).

The translation models may be available in different qualities; this app (`generate.py`) always prefers the highest available quality.

1. **`base`** - Highest quality, largest size
2. **`base-memory`** - Medium quality, optimized for memory
3. **`tiny`** - Fastest, smallest size


Check `download.py` for an example of how to download the files.

## Verification

After copying files, restart the Translator app. Available languages should appear automatically.
