#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
栖·生活工作台 — 打包前强制检阅脚本
职责：
1. 解析 index.html 内所有 <script>...</script> 块
2. Node 端 new Function() 验证每块语法
3. 扫描所有 onclick="xxx()" 与 javascript:xxx,确认 xxx 在脚本里有定义（window.xxx= 或 function xxx）
4. 扫描孤立 ';';孤立 '};;';'})();' 缺失等结构可疑点
5. 任何问题返回非零退出码，阻断 Gradle 构建
"""
import re, sys, subprocess, json, os

ROOT = os.path.dirname(os.path.abspath(__file__))
HTML = os.path.join(ROOT, 'index.html')
NODE = r'C:\Users\Feiqi\.workbuddy\binaries\node\versions\22.22.2\node.exe'

errors = []
warnings = []

def err(msg):
    errors.append(msg); print('[FAIL]', msg)
def warn(msg):
    warnings.append(msg); print('[WARN]', msg)
def ok(msg):
    print('[ OK ]', msg)

with open(HTML, 'r', encoding='utf-8') as f:
    html = f.read()

# === 1. 收集所有脚本块 ===
scripts = list(re.finditer(r'<script>([\s\S]*?)</script>', html))
print(f'\n=== 发现 {len(scripts)} 个 <script> 块 ===')

if not scripts:
    err('没有任何 <script> 块')
    sys.exit(1)

# === 1b. 剥除脚本块得到纯 HTML，给 onclick 扫描用 ===
html_no_scripts = re.sub(r'<script>[\s\S]*?</script>', '', html)
# === 1c. 剥除 style 块（避免把 CSS 里的 onclick 误识）===
html_no_scripts = re.sub(r'<style>[\s\S]*?</style>', '', html_no_scripts)
# === 1d. 剥除 HTML 注释 ===
html_no_scripts = re.sub(r'<!--[\s\S]*?-->', '', html_no_scripts)

# === 2. Node 端验证每块语法 ===
combined = []
for i, m in enumerate(scripts):
    content = m.group(1).strip()
    if not content:
        continue
    js_path = os.path.join(ROOT, f'.workbuddy\\_check_script_{i}.js')
    with open(js_path, 'w', encoding='utf-8') as f:
        f.write(content)
    try:
        r = subprocess.run([NODE, '-e',
            f'const fs=require("fs");const c=fs.readFileSync({json.dumps(js_path)},"utf8");try{{new Function(c);console.log("OK");}}catch(e){{console.log("ERR:"+e.message);process.exit(1);}}'
        ], capture_output=True, text=True, timeout=30)
        if r.returncode == 0:
            ok(f'script[{i}] 语法 OK ({len(content)} bytes)')
        else:
            err(f'script[{i}] 语法错误: {r.stdout.strip() or r.stderr.strip()}')
    except Exception as e:
        err(f'script[{i}] Node 验证失败: {e}')
    finally:
        if os.path.exists(js_path):
            try:
                os.remove(js_path)
            except OSError:
                pass  # 沙箱回收站不可用时忽略清理失败
    combined.append(content)

# === 3. 合并所有 script 内容作交叉引用 ===
# 跳过脚本注释里的 onclick="..." 占位（避免误报）
full_src = '\n;\n'.join(combined)
full_src_scan = '\n'.join(
    re.sub(r'^\s*//.*$', '', line) for line in full_src.split('\n')
)

# === 4. 收集所有定义的函数名 ===
# window.xxx = ... 或 window['xxx'] = ...
fn_defs = set()
fn_defs |= set(re.findall(r'window\.([A-Za-z_$][\w$]*)\s*=', full_src))
# function xxx() 声明（注意必须是行首独立 function）
fn_defs |= set(re.findall(r'^\s*function\s+([A-Za-z_$][\w$]*)\s*\(', full_src, flags=re.M))
# const xxx = function|() => ...
fn_defs |= set(re.findall(r'\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:function|\()', full_src))
# const xxx = (...)=>...
fn_defs |= set(re.findall(r'\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*\([^)]*\)\s*=>', full_src))
# const xxx = y => ...   y 名称
fn_defs |= set(re.findall(r'\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*[A-Za-z_$][\w$]*\s*=>', full_src))

print(f'\n=== 脚本内定义函数 {len(fn_defs)} 个 ===')

# === 5. 检查所有 onclick="..." 引用的函数是否已定义 ===
# onclick 在本项目中通常写在模板字符串里（动态 innerHTML），所以扫描整份 JS。
onclick_refs = re.findall(r'onclick\s*=\s*"([A-Za-z_$][\w$]*)\s*\(', full_src_scan)
onclick_refs += re.findall(r"onclick\s*=\s*'([A-Za-z_$][\w$]*)\s*\(", full_src_scan)
print(f'\n=== JS 模板中 onclick 引用 {len(onclick_refs)} 处 ===')

# 排除常见内建 / DOM 属性
SAFE = {'go','closeSheet','openSheet','toast','confirm','alert','prompt',
        'toggleEventDone','openEventSheet','openShopSheet','quickHabit',
        'setAccTab','setCollFilter','setCollView','renderAccount','renderCollection',
        'exportAccountExcel','editBudget','editCatBudget','saveBudget','saveCatBudget',
        'setTxType','saveTx','openHabitSheet','delHabit','habitDec','habitInc','saveHabit',
        'editWeekPlan','openFitSheet','saveFit','saveWeekPlan','schedPick','schedNextMonth',
        'schedPrevMonth','schedToday','saveEvent','saveShop','clearBought','openCollDetail',
        'openCollSheet','saveColl','editColl','delColl','saveEditColl','exportBackup',
        'manualBackup','clearAll','openHistorySheet','saveShopItem','handleImport',
        'updateStatusOptions','toggleHabitTarget','saveHabitVal','openTxSheet',
        'document','window'}

missing = []
for ref in set(onclick_refs):
    if ref in SAFE or ref in fn_defs:
        continue
    # Some are functions like document.getElementById — skip dot access
    if '.' in ref:
        continue
    missing.append(ref)

# 也得检查 SAFE 里的名字是否真的在脚本中暴露
bad_safe = []
for s in SAFE:
    if s in onclick_refs and s not in fn_defs and s not in {'confirm','alert','prompt','document','window','toast'}:
        # toast / go 等是 window.xxx 或 IIFE 函数, 列入 fn_defs 应有的集中。
        bad_safe.append(s)

# 检查 SAFE 集合里的所有函数是否真的在脚本中
safe_missing = []
for s in SAFE:
    if s in onclick_refs and s not in fn_defs:
        if s not in {'document','window','confirm','alert','prompt'}:
            # 检查是否是 window.xxx 形式
            if not re.search(rf'window\.{s}\b', full_src):
                safe_missing.append(s)

if missing:
    warn(f'HTML onclick 引用了但未在脚本中找到定义: {sorted(set(missing))}')
if safe_missing:
    err(f'HTML onclick 引用核心函数但脚本中缺失（必须在 expose 列表里）: {sorted(set(safe_missing))}')

# === 6. 检查结构可疑点 ===
# 6a. 连续两个 }; (孤立)
double_semi = list(re.finditer(r'\}\;\s*\n\s*\}\;', full_src))
if double_semi:
    lines = [full_src[:m.start()].count('\n')+1 for m in double_semi]
    msg = u"JS 中发现 %d 处疑似孤立 `};`（行 %s）" % (len(double_semi), lines)
    err(msg)

# 6b. 开头IIFE /(function()/}+ ... + '})()' 的不匹配
# 注：full_src 是去除了 <script> 标签的 JS 主体，须以 )();  或 )();\n 收尾
m_end = re.search(r'\}\)\(\)\s*;?\s*$', full_src.rstrip())
if not m_end:
    # 显式打印末尾内容以便诊断
    err('IIFE 末尾 `})();` 未在 JS 主体末尾发现（结构可能损坏）。末尾内容：' + repr(full_src[-120:]))

# 6c. JS 二进制结构平衡：用简单计数看 {} 是否平衡
opens = full_src.count('{'); closes = full_src.count('}')
if opens != closes:
    err(f'JS 大括号不平衡: {{ = {opens}, }} = {closes}，差 {opens-closes}')

# === 7. 总结 ===
print('\n=== 检阅总结 ===')
print(f'  Errors  : {len(errors)}')
print(f'  Warnings: {len(warnings)}')

if errors:
    print('\n✗ 检阅未通过，请修复上述问题后再构建。')
    sys.exit(1)
if warnings:
    print('\n⚠ 通过检阅但有警告，构建可继续。')
else:
    print('\n✓ 完全通过。')

sys.exit(0)
