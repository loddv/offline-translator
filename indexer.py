import dataclasses
import json
import subprocess

from dataclasses import dataclass
from functools import partial
from pathlib import Path

from data import LANGUAGE_NAMES, LANGUAGE_SCRIPTS

MODELS_URL = "https://media.githubusercontent.com/media/mozilla/firefox-translations-models/{commit}/models/{category}/{{lang_pair}}/{{fname}}.gz"

repo_dir = Path("~/git/firefox-translations-models/models/").expanduser()
assert repo_dir.exists()
model_types = ["tiny", "base", "base-memory"]
available = {}
for type_ in model_types:
    available[type_] = [p.name for p in (repo_dir / type_).iterdir()]

best_cat_for_model = {}
for type_ in model_types:  # goes in worse -> best order
    for model in available[type_]:
        best_cat_for_model[model] = type_


def not_eng(s: str) -> str:
    a, b = s[0:2], s[2:]
    if a == "en":
        return b
    return a


# flattened
all_langs = [x for xs in available.values() for x in xs]
all_langs = sorted(set(not_eng(x) for x in all_langs))


@dataclass(frozen=True)
class IndexFile:
    name: str
    size_bytes: int
    release_date: int
    url: str


def file_size(repo_path: Path, quality: str, lang_pair: str, fname: str) -> int:
    fpath = repo_path / quality / lang_pair / f"{fname}.gz"
    return fpath.stat().st_size


def generate_files_for_language(
    repo_path: Path, quality: str, lang_pair: str, rd: int, base_url
) -> dict[str, IndexFile]:
    model = f"model.{lang_pair}.intgemm.alphas.bin"
    lex = f"lex.50.50.{lang_pair}.s2t.bin"

    # Split vocab for Chinese and Japanese
    split_vocab_langs = {"enzh", "enja", "enko"}
    is_split_vocab = lang_pair in split_vocab_langs

    if is_split_vocab:
        src_vocab = f"srcvocab.{lang_pair}.spm"
        tgt_vocab = f"trgvocab.{lang_pair}.spm"
    else:
        vocab_file = f"vocab.{lang_pair}.spm"
        src_vocab = vocab_file
        tgt_vocab = vocab_file

    fsize = partial(file_size, repo_path, quality, lang_pair)
    return {
        "model": IndexFile(
            name=model, size_bytes=fsize(model), release_date=rd, url=base_url.format(fname=model, lang_pair=lang_pair)
        ),
        "lex": IndexFile(
            name=lex, size_bytes=fsize(lex), release_date=rd, url=base_url.format(fname=lex, lang_pair=lang_pair)
        ),
        "src_vocab": IndexFile(
            name=src_vocab,
            size_bytes=fsize(src_vocab),
            release_date=rd,
            url=base_url.format(fname=src_vocab, lang_pair=lang_pair),
        ),
        "tgt_vocab": IndexFile(
            name=tgt_vocab,
            size_bytes=fsize(tgt_vocab),
            release_date=rd,
            url=base_url.format(fname=tgt_vocab, lang_pair=lang_pair),
        ),
    }


def get_release_date_and_commit(repo_path: Path, quality: str, lang_pair: str) -> tuple[int, str]:
    cwd = repo_path / quality / lang_pair
    cmd = ["git", "log", "-1", "--format=%H;%at", "."]
    proc = subprocess.run(cmd, cwd=cwd, stdout=subprocess.PIPE, check=True)
    stdout = proc.stdout.decode().strip()
    commit, tstamp = stdout.split(";")
    tstamp = int(tstamp)
    return (tstamp, commit)


class EnhancedJSONEncoder(json.JSONEncoder):
    def default(self, o):
        if dataclasses.is_dataclass(o):
            return dataclasses.asdict(o)
        return super().default(o)


def main():
    index = []
    for lang in all_langs:
        model_from = f"{lang}en"
        model_to = f"en{lang}"
        pair_data_from = None
        pair_data_to = None

        if cat := best_cat_for_model.get(model_from):
            rd, commit = get_release_date_and_commit(repo_dir, cat, model_from)
            url = MODELS_URL.format(commit=commit, category=cat)
            pair_data_from = generate_files_for_language(repo_dir, cat, model_from, rd, url)

        if cat := best_cat_for_model.get(model_to):
            rd, commit = get_release_date_and_commit(repo_dir, cat, model_to)
            url = MODELS_URL.format(commit=commit, category=cat)
            pair_data_to = generate_files_for_language(repo_dir, cat, model_to, rd, url)

        index_language = {
            "code": lang,
            "to": pair_data_to,
            "from": pair_data_from,
            "name": LANGUAGE_NAMES[lang],
            "script": LANGUAGE_SCRIPTS[lang],
            "extra_files": [],
        }
        index.append(index_language)

    json.dump(
        {"languages": index},
        open("index.json", "w"),
        cls=EnhancedJSONEncoder,
        indent=2,
    )


main()
