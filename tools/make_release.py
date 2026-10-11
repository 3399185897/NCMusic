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

TAG = 'v1.1.1'
APK = 'releases/NCMusic-v1.1.1-release.apk'


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
            'name': 'v1.1.1 —— 音质、下载与主题系统修复',
            'body': (
                '## 修复\n'
                '- **音质**：选择无损/母带却拿到普通音质（旧接口无对应码率），改用 v1 接口的 level 参数\n'
                '- **未登录无法播放**：平台对未登录用户不返回播放地址，此前失败静默，现明确提示原因\n'
                '- **下载位置不生效**：设置的自定义目录此前从未被读取\n'
                '- **深色模式元素不可见**：根布局未提供 contentColor，黑底仍用黑色前景\n'
                '- **开启取色后深色失效**：主色来源优先级错误\n'
                '- **开关图标显示为叉**：用加号旋转 45° 模拟对勾，实际得到叉号\n'
                '- **滑动条刻度与数值错位**、播放器页长音质名被裁切、统计卡配色区分度不足\n'
                '- **颜色切换动画**收尾跳变\n\n'
                '## 新增\n'
                '- **M3 Expressive 加载动效**：官方 7 种 MaterialShapes 弹簧变形 + 4666ms 匀速旋转\n'
                '- **本地音乐「已下载」分类**，支持查看与删除\n'
                '- **下载时同步保存封面与歌词**，作者信息写入 LRC 标准标签\n'
                '- **歌词页跟随封面动态取色**\n'
                '- **主页组件开关**：可独立隐藏雷达歌单与推荐歌单\n'
                '- 深色模式改为三联滑块（开启 / 跟随系统 / 关闭）\n\n'
                '## 变更\n'
                '- 深色配色改为**纯黑底 + 灰阶强调**\n'
                '- 播放地址获取改为**逐级降级**，最差退到标准音质保证能出声\n\n'
                '## 安装\n'
                '下载 `NCMusic-v1.1.1-release.apk` 直接安装（需 Android 8.0+）。'
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
