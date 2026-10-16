"""创建 GitHub Release 并上传 APK。

token 从环境变量 GH_TOKEN 读取（由外层 shell 注入，避免出现在命令行/日志中）。
"""
import json
import os
import urllib.error
import urllib.request

REPO = '3399185897/NCMusic'
TOKEN = os.environ.get('GH_TOKEN', '')
if not TOKEN:
    raise SystemExit('缺少 GH_TOKEN')

TAG = 'v1.2.0'
APK = 'releases/NCMusic-v1.2.0-release.apk'


def api(url, method='GET', data=None, raw=None, ctype='application/json'):
    headers = {
        'Authorization': 'token ' + TOKEN,
        'Accept': 'application/vnd.github+json',
        'User-Agent': 'NCMusic-release',
    }
    body = None
    if raw is not None:
        body = raw
        headers['Content-Type'] = ctype
    elif data is not None:
        body = json.dumps(data).encode()
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(url, data=body, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            txt = r.read().decode() or '{}'
            return r.status, json.loads(txt)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:400]


# ---- 1) 身份 ----
st, d = api('https://api.github.com/user')
print('[1] 身份校验:', st, d.get('login') if isinstance(d, dict) else str(d)[:120])
if st != 200:
    raise SystemExit('token 无效或无权限')

# ---- 2) 是否已有该 tag 的 release ----
st, d = api('https://api.github.com/repos/%s/releases/tags/%s' % (REPO, TAG))
if st == 200 and isinstance(d, dict):
    rid = d['id']
    print('[2] 已存在 Release:', TAG, 'id=', rid)
else:
    print('[2] 创建新 Release:', TAG)
    st, d = api(
        'https://api.github.com/repos/%s/releases' % REPO,
        method='POST',
        data={
            'tag_name': TAG,
            'target_commitish': 'main',
            'name': 'v1.2.0 —— 核心 Bug、性能与安全专项',
            'body': (
                '本次以**代码质量专项**为主线，按四个阶段系统排查并修复核心 Bug、状态与性能、UI 与动画，以及安全与发布配置。\n'
                '\n'
                '### 播放\n'
                '- 本地歌曲（SAF 的 `content://` 路径）无法播放\n'
                '- 播放失败后卡在「正在播放」；解析地址期间按暂停无效\n'
                '- 切歌长时间静音；音质降级提示被清理而始终不可见\n'
                '\n'
                '### 下载\n'
                '- 下载中断会把已有的完整文件截断\n'
                '- SAF 替换流程可能「旧文件已删、新文件没有」\n'
                '- 新增下载进度条（总长未知时用不确定进度样式）\n'
                '\n'
                '### 性能与状态\n'
                '- 歌单与听歌历史被每 30 秒重载一次\n'
                '- 缓存淘汰会删掉**正在下载**的临时文件\n'
                '- 首页 loading 提前结束；搜索清空后进度条不复位\n'
                '\n'
                '### 界面\n'
                '- 修复「每日推荐丢失需重新登录」（启动 Cookie 恢复竞态）\n'
                '- 音质模块按官方重做（档位、名称、规格逐项对齐）\n'
                '- 歌词跟随封面取色；颜色圆点、阴影与加载态统一\n'
                '- 关于页新增「检查更新」\n'
                '\n'
                '### 安全\n'
                '- 明文流量由全局开关改为按域收窄\n'
                '- 媒体会话仅放行可信控制端\n'
                '\n'
                '完整改动见 [CHANGELOG.md](https://github.com/3399185897/NCMusic/blob/main/CHANGELOG.md)\n'
            ),
            'draft': False,
            'prerelease': False,
        },
    )
    if st not in (200, 201):
        raise SystemExit('创建失败: %s %s' % (st, d))
    rid = d['id']
    print('[2] 创建成功 id=', rid)

# ---- 3) 上传 APK ----
with open(APK, 'rb') as f:
    blob = f.read()
print('[3] 上传 APK：%d 字节' % len(blob))

name = os.path.basename(APK)
st, d = api(
    'https://uploads.github.com/repos/%s/releases/%d/assets?name=%s' % (REPO, rid, name),
    method='POST', raw=blob, ctype='application/vnd.android.package-archive',
)
if st in (200, 201):
    print('[3] 上传成功:', d.get('browser_download_url'))
else:
    if isinstance(d, str) and 'already_exists' in d:
        print('[3] 该资源已存在，跳过')
    else:
        print('[3] 上传结果:', st, d)

# ---- 4) 汇总 ----
st, d = api('https://api.github.com/repos/%s/releases/tags/%s' % (REPO, TAG))
if st == 200:
    print()
    print('Release 页面:', d.get('html_url'))
    print('资源列表:')
    for a in d.get('assets', []):
        print('  - %s  (%d KB, 下载 %d 次)' % (a['name'], a['size'] // 1024, a['download_count']))
