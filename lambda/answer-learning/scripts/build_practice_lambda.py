"""Package only the learning worker and its explicit dependencies (no env or user data)."""
import argparse
import hashlib
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]
SOURCE_FILES = ['app/__init__.py', 'app/practice_grading/__init__.py',
                'app/practice_grading/contracts.py', 'app/practice_grading/provider.py',
                'app/practice_grading/settings.py']


def package(dependencies, output):
    sources = [ROOT / name for name in SOURCE_FILES] + sorted((ROOT / 'app/practice_lambda').glob('*.py'))
    with ZipFile(output, 'w', compression=ZIP_DEFLATED) as z:
        for p in sorted(dependencies.rglob('*')):
            if p.is_file() and '__pycache__' not in p.parts and not p.name.endswith('.pyc'):
                z.write(p, str(p.relative_to(dependencies)))
        for p in sources:
            z.write(p, str(p.relative_to(ROOT)))
    print(f'{output.name}: {output.stat().st_size} bytes; sha256={hashlib.sha256(output.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--dependencies', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    package(args.dependencies, args.output)
