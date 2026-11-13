"""A范围检查：每轮开始拍基线，结束比较所有非A文件。只检测，不自动回退任何文件。"""
from pathlib import Path
import argparse
import hashlib
import json
import os

ALLOWED = (
    'app/src/main/java/com/example/localtaskassistant/capture/',
    'app/src/main/java/com/example/localtaskassistant/inference/',
    'app/src/test/java/com/example/localtaskassistant/capture/',
    'app/src/test/java/com/example/localtaskassistant/inference/',
    'app/src/androidTest/java/com/example/localtaskassistant/capture/',
    'app/src/androidTest/java/com/example/localtaskassistant/inference/',
    'docs/A阶段/',
)
IGNORED_ROOT_DIRS = {'.git', '.idea', '.gradle', '.kotlin', '.android-local', '.build-home', 'build'}

def is_allowed(relative):
    return any(relative.startswith(prefix) for prefix in ALLOWED)

def inventory(root):
    """返回非A文件的内容哈希；local.properties、schema和公共文档都受保护。"""
    root = Path(root).resolve()
    result = {}
    for directory, dirs, names in os.walk(root):
        current = Path(directory)
        dirs[:] = [name for name in dirs if name != '__pycache__']
        if current == root:
            dirs[:] = [name for name in dirs if name not in IGNORED_ROOT_DIRS]
        if current == root / 'app':
            dirs[:] = [name for name in dirs if name != 'build']
        for name in names:
            path = current / name
            relative = path.relative_to(root).as_posix()
            if not is_allowed(relative):
                result[relative] = hashlib.sha256(path.read_bytes()).hexdigest()
    return result

def compare(before, after):
    return {
        'modified': sorted(path for path in before.keys() & after.keys() if before[path] != after[path]),
        'added': sorted(after.keys() - before.keys()),
        'deleted': sorted(before.keys() - after.keys()),
    }

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--snapshot', help='新基线路径，只能在docs/A阶段内；禁止覆盖旧基线')
    group.add_argument('--check', help='本轮开始时拍下的基线路径')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    if args.snapshot:
        path = (root / args.snapshot).resolve()
        if not path.is_relative_to((root / 'docs/A阶段').resolve()):
            raise SystemExit('基线只能写入A自己的docs/A阶段目录')
        if path.exists():
            raise SystemExit('基线已存在，不覆盖；请为新一轮使用新文件名')
        record = {'schema': 1, 'allowed_prefixes': list(ALLOWED), 'protected': inventory(root)}
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding='utf-8')
        print(f'已记录{len(record["protected"])}个非A文件：{path}')
        return
    path = (root / args.check).resolve()
    record = json.loads(path.read_text(encoding='utf-8'))
    if record.get('schema') != 1 or record.get('allowed_prefixes') != list(ALLOWED):
        raise SystemExit('基线格式或白名单不同，停止检查；不能通过改白名单放过越界')
    changes = compare(record['protected'], inventory(root))
    if any(changes.values()):
        print('发现非A文件变化。本轮不得继续开发或合入；先核对是否来自其他成员。')
        for kind, paths in changes.items():
            for item in paths:
                print(f'{kind}: {item}')
        raise SystemExit(1)
    print(f'PASS：{len(record["protected"])}个非A文件内容未变；A范围检查通过')

if __name__ == '__main__':
    main()
