"""创建 GitHub Release 并上传 APK（纯标准库，带网络重试）。

## 为什么不用 git 之外的库

本项目此前用 `tools/make_release.py`，但它依赖 `GH_TOKEN` 环境变量。
实际环境里 token 存在 git 凭据库（`git-credential-wincred`），
且网络对 `api.github.com` 存在间歇性阻断 —— 单次请求经常超时。

因此这个脚本：
  · 从 git 凭据库自动取 token，不要求在命令行/环境变量里明文出现
  · 所有请求带**多次重试 + 退避**，因为实测失败率约 50%
  · 走本地代理（可用时），失败再回退直连

用法：
    python tools/publish_release.py 1.3.0
"""
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

REPO = os.environ.get('REPO', '3399185897/NCMusic')
PROXY = os.environ.get('GHPROXY', 'http://127.0.0.1:11864')
API = 'https://api.github.com'


def _token() -> str:
    """从 git 凭据库读取 token（不在命令行明文传递）。"""
    env = os.environ.get('GH_TOKEN')
    if env:
        return env.strip()
    out = subprocess.run(
        ['git', 'credential-wincred', 'get'],
        input='protocol=https\nhost=github.com\n\n',
        capture_output=True, text=True,
    ).stdout
    for line in out.splitlines():
        if line.startswith('password='):
            return line[len('password='):].strip()
    raise SystemExit('未能获取 GitHub token')


TOKEN = _token()


def _opener(use_proxy: bool):
    if use_proxy and PROXY:
        op = urllib.request.build_opener(
            urllib.request.ProxyHandler({'http': PROXY, 'https': PROXY})
        )
        return op
    return urllib.request.build_opener(urllib.request.ProxyHandler({}))


def api(path, method='GET', data=None, raw=None, ctype='application/json',
        retries=8, use_proxy=True):
    """调用 GitHub API，失败自动重试（网络间歇性阻断）。"""
    url = path if path.startswith('http') else API + path
    headers = {
        'Authorization': 'Bearer ' + TOKEN,
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

    last = None
    for i in range(retries):
        # 交替用「代理 / 直连」，任一通路可用即可
        proxy_now = use_proxy and (i % 3 != 2)
        req = urllib.request.Request(url, data=body, method=method, headers=headers)
        try:
            with _opener(proxy_now).open(req, timeout=180) as r:
                txt = r.read().decode() or '{}'
                return r.status, json.loads(txt)
        except urllib.error.HTTPError as e:
            detail = e.read().decode()[:500]
            # 4xx 是业务错误（如 already_exists），不再重试
            if 400 <= e.code < 500:
                return e.code, detail
            last = f'HTTP {e.code}: {detail}'
        except Exception as e:  # 网络层
            last = f'{type(e).__name__}: {e}'
        time.sleep(min(2 + i, 6))
    return 0, last


def main():
    version = sys.argv[1] if len(sys.argv) > 1 else '1.3.0'
    tag = 'v' + version
    apk = f'releases/NCMusic-{tag}-release.apk'
    if not os.path.exists(apk):
        raise SystemExit(f'找不到产物：{apk}')

    notes_path = f'RELEASE_NOTES_{tag}.md'
    if os.path.exists(notes_path):
        body = open(notes_path, encoding='utf-8').read()
    else:
        body = f'{tag} 发布。详见 CHANGELOG.md。'
    print(f'[1/3] 发布说明 {len(body)} 字符')

    # ---- 创建 release（已存在则复用）----
    payload = {
        'tag_name': tag,
        'target_commitish': 'main',
        'name': f'云音 NCMusic {tag}',
        'body': body,
        'draft': False,
        'prerelease': False,
    }
    st, resp = api(f'/repos/{REPO}/releases', 'POST', data=payload)
    if st in (200, 201):
        rel = resp
        print(f'[2/3] Release 已创建 id={rel["id"]}')
    elif st == 422 and isinstance(resp, str) and 'already_exists' in resp:
        st2, rel = api(f'/repos/{REPO}/releases/tags/{tag}')
        if st2 != 200:
            raise SystemExit(f'取已存在 Release 失败：{rel}')
        print(f'[2/3] Release 已存在，复用 id={rel["id"]}')
    else:
        raise SystemExit(f'创建 Release 失败 {st}: {resp}')

    # ---- 删除同名 asset（避免 422 already_exists）----
    st, assets = api(f'/repos/{REPO}/releases/{rel["id"]}/assets')
    if st == 200:
        for a in assets:
            if a['name'] == os.path.basename(apk):
                api(f'/repos/{REPO}/releases/assets/{a["id"]}', 'DELETE')
                print(f'      已移除旧 asset：{a["name"]}')

    # ---- 上传 APK ----
    size = os.path.getsize(apk)
    print(f'[3/3] 上传 {apk} ({size / 1048576:.1f} MB)…')
    with open(apk, 'rb') as f:
        blob = f.read()
    st, up = api(
        f'https://uploads.github.com/repos/{REPO}/releases/{rel["id"]}/assets'
        f'?name={os.path.basename(apk)}',
        'POST', raw=blob, ctype='application/vnd.android.package-archive',
        retries=6,
    )
    if st in (200, 201):
        print(f'✓ 上传成功 {up["size"] / 1048576:.1f} MB')
        print(f'  下载：https://github.com/{REPO}/releases/download/{tag}/'
              f'{os.path.basename(apk)}')
        print(f'  页面：https://github.com/{REPO}/releases/tag/{tag}')
    else:
        raise SystemExit(f'上传失败 {st}: {up}')


if __name__ == '__main__':
    main()