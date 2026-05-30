#!/usr/bin/env python3
"""
tools/fix_indent.py - 一键修复缩进 & 去特殊字符
用法：python tools/fix_indent.py scripts/xxx.py
"""
import sys, re, argparse

def fix_file(path: str):
    with open(path, 'r', encoding='utf-8') as f:
        raw = f.read()

    # 1. 去 BOM & 特殊空白
    cleaned = raw.replace('\ufeff', '').replace('\t', '    ')
    # 2. 去行尾空格
    cleaned = '\n'.join(line.rstrip() for line in cleaned.splitlines())
    # 3. 确保文件末尾有且只有一个换行
    cleaned = cleaned.rstrip() + '\n'

    with open(path, 'w', encoding='utf-8') as f:
        f.write(cleaned)
    print(f"已修复：{path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='修复 Python 缩进和特殊字符')
    parser.add_argument('file', help='要修复的 .py 文件')
    args = parser.parse_args()
    fix_file(args.file)
