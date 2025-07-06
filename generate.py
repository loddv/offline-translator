#!/usr/bin/env python3
"""
Generate Kotlin enum class for language pairs from model repository structure.
Usage: python generate_language_enum.py <repository_path>
"""

import os
import sys
from pathlib import Path
from typing import Dict, Set, Tuple

# Language pairs will be validated by parsing and checking against LANGUAGE_NAMES

# Language code to display name mapping
LANGUAGE_NAMES = {
    'ar': 'Arabic',
    'az': 'Azerbaijani',
    'be': 'Belarusian', 
    'bg': 'Bulgarian',
    'bn': 'Bengali',
    'bs': 'Bosnian',
    'ca': 'Catalan',
    'cs': 'Czech',
    'da': 'Danish',
    'de': 'German',
    'el': 'Greek',
    'en': 'English',
    'es': 'Spanish',
    'et': 'Estonian',
    'fa': 'Persian',
    'fi': 'Finnish',
    'fr': 'French',
    'gu': 'Gujarati',
    'he': 'Hebrew',
    'hi': 'Hindi',
    'hr': 'Croatian',
    'hu': 'Hungarian',
    'id': 'Indonesian',
    'is': 'Icelandic',
    'it': 'Italian',
    'ja': 'Japanese',
    'kn': 'Kannada',
    'ko': 'Korean',
    'lt': 'Lithuanian',
    'lv': 'Latvian',
    'ml': 'Malayalam',
    'ms': 'Malay',
    'mt': 'Maltese',
    'nb': 'Norwegian Bokmål',
    'nl': 'Dutch',
    'nn': 'Norwegian Nynorsk',
    'pl': 'Polish',
    'pt': 'Portuguese',
    'ro': 'Romanian',
    'ru': 'Russian',
    'sk': 'Slovak',
    'sl': 'Slovenian',
    'sq': 'Albanian',
    'sr': 'Serbian',
    'sv': 'Swedish',
    'ta': 'Tamil',
    'te': 'Telugu',
    'tr': 'Turkish',
    'uk': 'Ukrainian',
    'vi': 'Vietnamese',
    'zh': 'Chinese'
}

TESSERACT_LANGUAGE_MAPPINGS = {
    'ar': 'ara',          # Arabic
    'az': 'aze',          # Azerbaijani
    'be': 'bel',          # Belarusian
    'bg': 'bul',          # Bulgarian
    'bn': 'ben',          # Bengali
    'bs': 'bos',          # Bosnian
    'ca': 'cat',          # Catalan
    'cs': 'ces',          # Czech
    'da': 'dan',          # Danish
    'de': 'deu',          # German
    'el': 'ell',          # Greek
    'en': 'eng',          # English
    'es': 'spa',          # Spanish
    'et': 'est',          # Estonian
    'fa': 'fas',          # Persian
    'fi': 'fin',          # Finnish
    'fr': 'fra',          # French
    'gu': 'guj',          # Gujarati
    'he': 'heb',          # Hebrew
    'hi': 'hin',          # Hindi
    'hr': 'hrv',          # Croatian
    'hu': 'hun',          # Hungarian
    'id': 'ind',          # Indonesian
    'is': 'isl',          # Icelandic
    'it': 'ita',          # Italian
    'ja': 'jpn',          # Japanese
    'kn': 'kan',          # Kannada
    'ko': 'kor',          # Korean
    'lt': 'lit',          # Lithuanian
    'lv': 'lav',          # Latvian
    'ml': 'mal',          # Malayalam
    'ms': 'msa',          # Malay
    'mt': 'mlt',          # Maltese
    'nb': 'nor',          # Norwegian Bokmål (using nor for Norwegian)
    'nl': 'nld',          # Dutch
    'nn': 'nor',          # Norwegian Nynorsk (using nor for Norwegian)
    'pl': 'pol',          # Polish
    'pt': 'por',          # Portuguese
    'ro': 'ron',          # Romanian
    'ru': 'rus',          # Russian
    'sk': 'slk',          # Slovak
    'sl': 'slv',          # Slovenian
    'sq': 'sqi',          # Albanian
    'sr': 'srp',          # Serbian
    'sv': 'swe',          # Swedish
    'ta': 'tam',          # Tamil
    'te': 'tel',          # Telugu
    'tr': 'tur',          # Turkish
    'uk': 'ukr',          # Ukrainian
    'vi': 'vie',          # Vietnamese
    'zh': 'chi_sim',      # Chinese (using simplified Chinese as default)
}

def extract_language_pairs(repo_path: str) -> Dict[str, Set[str]]:
    """
    Extract language pairs from repository structure.
    Returns dict mapping model_type -> set of language pairs
    Validates that all found pairs are in the supported list.
    """
    repo_path = Path(repo_path)
    language_pairs = {'base': set(), 'base-memory': set(), 'tiny': set()}
    
    # Scan models/base, models/base-memory, and models/tiny directories
    for model_type in ['base', 'base-memory', 'tiny']:
        models_dir = repo_path / 'models' / model_type
        if models_dir.exists():
            for item in models_dir.iterdir():
                if item.is_dir():
                    # Directory name should be language pair (e.g., 'enar', 'enru')
                    pair_name = item.name
                    src, tgt = parse_language_pair(pair_name)
                    if src not in LANGUAGE_NAMES or tgt not in LANGUAGE_NAMES:
                        print(f"Error: Unsupported language pair '{pair_name}' found in {model_type}")
                        print(f"Supported pairs: {sorted(LANGUAGE_NAMES.keys())}")
                        sys.exit(1)
                    language_pairs[model_type].add(pair_name)
    
    return language_pairs

def parse_language_pair(pair: str) -> Tuple[str, str]:
    """
    Parse language pair string to extract source and target language codes.
    Asserts the pair is exactly 4 characters and splits it in half.
    """
    if len(pair) != 4:
        print(f"Error: Language pair '{pair}' must be exactly 4 characters")
        sys.exit(1)
    
    # Split the 4-character pair in half
    src = pair[:2]
    tgt = pair[2:]
    
    return src, tgt

def get_non_english_language(pair: str) -> Tuple[str, str]:
    """
    Get the non-English language code and name from a language pair.
    Returns (language_code, language_name)
    Validates that the language code exists in LANGUAGE_NAMES.
    """
    src, tgt = parse_language_pair(pair)
    
    if src == 'en':
        lang_code = tgt
    elif tgt == 'en':
        lang_code = src
    else:
        # If neither is English, return the target language
        lang_code = tgt
    
    if lang_code not in LANGUAGE_NAMES:
        print(f"Error: Language code '{lang_code}' from pair '{pair}' not found in supported language names")
        print(f"Supported language codes: {sorted(LANGUAGE_NAMES.keys())}")
        sys.exit(1)
    
    return lang_code, LANGUAGE_NAMES[lang_code]

def get_best_model_type(model_types: Set[str]) -> str:
    """
    Get the best model type based on priority: base > base-memory > tiny
    """
    if 'base' in model_types:
        return 'base'
    elif 'base-memory' in model_types:
        return 'base-memory'
    elif 'tiny' in model_types:
        return 'tiny'
    else:
        raise ValueError(f"No valid model type found in {model_types}")

def generate_kotlin_enum(language_pairs: Dict[str, Set[str]]) -> str:
    """
    Generate Kotlin enum classes for Language and LanguagePair.
    """
    # Collect all unique languages
    all_languages = set()
    for pairs in language_pairs.values():
        for pair in pairs:
            src_code, tgt_code = parse_language_pair(pair)
            all_languages.add(src_code)
            all_languages.add(tgt_code)
    
    all_languages.add("en")

    # Generate Language enum entries
    language_entries = []
    for lang_code in sorted(all_languages):
        lang_name = LANGUAGE_NAMES[lang_code]
        enum_name = lang_name.upper().replace(' ', '_').replace('Å', 'A')
        language_entries.append(f'    {enum_name}("{lang_code}", "{lang_name}")')

    language_entries = sorted(language_entries)
    
    # Collect all unique language pairs with their model types
    pairs_data = {}  # pair -> (src_code, tgt_code, model_types)
    
    for model_type, pairs in language_pairs.items():
        for pair in pairs:
            src_code, tgt_code = parse_language_pair(pair)
            
            # Assert that pairs are only to or from English
            if src_code != 'en' and tgt_code != 'en':
                print(f"Error: Language pair '{pair}' is not to or from English")
                print(f"Only English-to-X or X-to-English pairs are supported")
                sys.exit(1)
            
            if pair not in pairs_data:
                pairs_data[pair] = (src_code, tgt_code, set())
            pairs_data[pair][2].add(model_type)
    
    # Separate into fromEnglish and toEnglish
    from_english = {}  # lang_code -> model_type
    to_english = {}    # lang_code -> model_type
    
    for pair, (src_code, tgt_code, model_types) in pairs_data.items():
        best_model_type = get_best_model_type(model_types)
        
        if src_code == 'en':
            # English to other language
            from_english[tgt_code] = best_model_type
        else:
            # Other language to English
            to_english[src_code] = best_model_type
    
    # Generate fromEnglish map entries
    from_english_entries = []
    for lang_code in sorted(from_english.keys()):
        lang_name = LANGUAGE_NAMES[lang_code]
        lang_enum = f'Language.{lang_name.upper().replace(" ", "_").replace("Å", "A")}'
        model_type_enum = f'ModelType.{from_english[lang_code].upper().replace("-", "_")}'
        from_english_entries.append(f'    {lang_enum} to {model_type_enum}')
    
    # Generate toEnglish map entries
    to_english_entries = []
    for lang_code in sorted(to_english.keys()):
        lang_name = LANGUAGE_NAMES[lang_code]
        lang_enum = f'Language.{lang_name.upper().replace(" ", "_").replace("Å", "A")}'
        model_type_enum = f'ModelType.{to_english[lang_code].upper().replace("-", "_")}'
        to_english_entries.append(f'    {lang_enum} to {model_type_enum}')
    
    # Generate the complete enum classes and maps
    kotlin_code = f"""enum class ModelType(val pathName: String) {{
    BASE("base"),
    BASE_MEMORY("base-memory"),
    TINY("tiny");
    
    override fun toString(): String = pathName
}}

enum class Language(val code: String, val displayName: String) {{
{',\n'.join(language_entries)}
}}

val fromEnglish = mapOf(
{',\n'.join(from_english_entries)}
)

val toEnglish = mapOf(
{',\n'.join(to_english_entries)}
)"""
    
    return kotlin_code

def main():
    if len(sys.argv) != 2:
        print("Usage: python generate_language_enum.py <repository_path>")
        sys.exit(1)
    
    repo_path = sys.argv[1]
    
    if not os.path.exists(repo_path):
        print(f"Error: Repository path '{repo_path}' does not exist")
        sys.exit(1)
    
    print(f"Scanning repository at: {repo_path}")
    
    # Extract language pairs
    language_pairs = extract_language_pairs(repo_path)
    
    print(f"Found {len(language_pairs['base'])} base language pairs")
    print(f"Found {len(language_pairs['base-memory'])} base-memory language pairs")
    print(f"Found {len(language_pairs['tiny'])} tiny language pairs")
    
    if not language_pairs['base'] and not language_pairs['base-memory'] and not language_pairs['tiny']:
        print("No language pairs found. Please check the repository structure.")
        sys.exit(1)
    
    # Generate Kotlin enum
    kotlin_code = generate_kotlin_enum(language_pairs)
    
    # Write to file
    output_file = "Language.kt"
    with open(output_file, 'w') as f:
        f.write(kotlin_code)
    
    print(f"Generated Kotlin enum class in {output_file}")
    print("\nPreview:")
    print(kotlin_code)

if __name__ == "__main__":
    main()
