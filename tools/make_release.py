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
                '本次为**代码质量专项**，按四个阶段系统性排查并修复核心 Bug、'
                '状态与性能、UI 与动画，以及安全与发布配置。\n\n'
                '## 播放修复\n'
                '- 本地歌曲（SAF 的 `content://` 路径）无法播放\n'
                '- 播放失败后 `isPlaying` 不复位，界面卡在"正在播放"\n'
                '- 解析地址期间按暂停无效，请求返回后仍自动播放\n'
                '- 快速切歌时取消顺序错误，偶发不显示加载态\n'
                '- 切歌长时间静音（静音时长 = 淡出 + 整个解析耗时）\n'
                '- 音质降级提示被后续清理抹掉，从未可见\n\n'
                '## 下载修复\n'
                '- 下载中断会把已有的完整文件截断\n'
                '- 同名并发下载互相覆盖临时数据\n'
                '- SAF 替换流程可能"旧文件已删、新文件没有"\n'
                '- 不同歌手的同名歌曲互相覆盖\n\n'
                '## 状态与性能\n'
                '- 首页 loading 提前结束；异常路径永久卡住\n'
                '- 歌单与听歌历史被每 30 秒重载一次\n'
                '- 搜索清空后进度条不复位；失败与空结果无法区分\n'
                '- 缓存淘汰会删掉**正在下载**的临时文件\n'
                '- 最大缓存设置默认值落在滑块范围外，实际失效\n\n'
                '## UI 与交互\n'
                '- **每日推荐丢失需重新登录**（启动 Cookie 恢复竞态）\n'
                '- 列表最后一项被悬浮岛遮挡；底部留白重复计算\n'
                '- 颜色扩散动画绘制在内容之下，完全不可见\n'
                '- 浅色配色仅定义 14 个角色，对比度不足\n'
                '- 排行榜卡片占满屏宽、标题溢出\n'
                '- 搜索加载样式统一为变形多边形指示器\n'
                '- 切歌封面按进度缩放淡入，形成连续转场\n\n'
                '## 无障碍\n'
                '- 系统「移除动画」现覆盖全部动画，且支持运行时切换\n\n'
                '## 安全\n'
                '- 明文流量由全局开关改为按域收窄（默认强制 HTTPS）\n'
                '- 媒体会话仅放行可信控制端\n'
                '- 复核确认 Cookie 未进入日志 / Intent / 备份\n\n'
                '## 构建\n'
                '- 新增 `.gitattributes`：`gradlew` 强制 LF、`gradlew.bat` 强制 CRLF\n\n'
                '## 安装\n'
                '下载 `NCMusic-v1.2.0-release.apk` 直接安装（需 Android 8.0+）。'
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
