"""不需要 Android SDK：检查 C 未越过 Facade，契约未依赖 Android，A 未直接写数据库。"""
from pathlib import Path
import re
root = Path(__file__).resolve().parents[1] / 'app/src/main/java/com/example/localtaskassistant'
violations = []
rules = {
    'presentation': [r'import .*\.(data|capture|inference|business|reminder)\.', r'import android\.app\.AlarmManager'],
    'inference': [r'import .*\.(data|presentation)\.'],
    'contract': [r'import android\.', r'import androidx\.'],
}
for directory, forbidden in rules.items():
    for file in (root / directory).rglob('*.kt'):
        for number, line in enumerate(file.read_text(encoding='utf-8').splitlines(), 1):
            if any(re.search(pattern, line) for pattern in forbidden):
                violations.append(f'{file.relative_to(root)}:{number}: {line.strip()}')
if violations:
    raise SystemExit('\n'.join(violations))
print('PASS: presentation / inference / contract dependency boundaries')
