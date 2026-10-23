#!/usr/bin/env bash
# 构建「给答辩现场双击就能打开」的发布包：
#   1) Vue 构建产物放进 Spring Boot 的 static 目录
#   2) 打成可执行 jar（demo profile 用内嵌 H2，不需要 MySQL）
#   3) 用 jpackage 生成自带 Java 运行时的免安装版
#   4) 生成源码 zip
#
# 运行前请先停掉正在使用 server/target 的后端进程。
# 构建完会把 server/src/main/resources/static 删掉 —— 那是构建产物，
# 留在源码里会让下次 mvn 打包带上上一版前端，改完不生效还很难查。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MVN="$ROOT/.tooling/apache-maven-3.9.9/bin/mvn"
WEB="$ROOT/web"
SERVER="$ROOT/server"
STATIC="$SERVER/src/main/resources/static"
RELEASE="$ROOT/release"
DEMO="$RELEASE/食刻点餐-演示"
APP_NAME="食刻点餐-免安装版"
APP_DIR="$RELEASE/$APP_NAME"
APP_BIN="Shike"

if [ ! -x "$MVN" ]; then
  echo "找不到 Maven：$MVN" >&2
  exit 1
fi

echo "==> 1/6 构建 Vue 前端"
cd "$WEB"
npm run build

echo "==> 2/6 把前端放进 Spring Boot static"
rm -rf "$STATIC"
mkdir -p "$STATIC"
cp -R "$WEB/dist/." "$STATIC/"

echo "==> 3/6 打包 Spring Boot 可执行 jar（含 H2）"
cd "$SERVER"
"$MVN" -B -q clean package -DskipTests
JAR="$(ls target/shike-ordering-*.jar | grep -v original | head -1)"
if [ -z "$JAR" ]; then
  echo "没有找到打出来的 jar" >&2
  exit 1
fi

echo "==> 4/6 组装 jar 演示包"
rm -rf "$DEMO"
mkdir -p "$DEMO"
cp "$JAR" "$DEMO/shike-ordering.jar"
cp "$ROOT/tools/demo/run-jar.bat" "$DEMO/run.bat"
cp "$ROOT/tools/demo/打开说明-jar.txt" "$DEMO/打开说明.txt"

python - "$DEMO" "$RELEASE/食刻点餐-演示.zip" <<'PY'
import sys, zipfile
from pathlib import Path
src, dst = Path(sys.argv[1]), Path(sys.argv[2])
with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for f in src.rglob("*"):
        if f.is_file():
            z.write(f, f.relative_to(src.parent))
print("wrote", dst)
PY

echo "==> 5/6 生成免安装版（jpackage）"
rm -rf "$RELEASE/$APP_BIN" "$APP_DIR" "$RELEASE/_app_input"
mkdir -p "$RELEASE/_app_input"
cp "$JAR" "$RELEASE/_app_input/shike-ordering.jar"

if command -v jpackage >/dev/null 2>&1; then
  if jpackage \
      --type app-image \
      --name "$APP_BIN" \
      --app-version 1.0.0 \
      --vendor "SHI KE" \
      --description "食刻点餐系统演示" \
      --dest "$RELEASE" \
      --input "$RELEASE/_app_input" \
      --main-jar shike-ordering.jar \
      --main-class org.springframework.boot.loader.launch.JarLauncher \
      --java-options "-Dspring.profiles.active=demo" \
      --java-options "-Dfile.encoding=UTF-8" \
      --add-modules java.base,java.sql,java.naming,java.management,java.instrument,java.net.http,java.desktop,java.logging,java.security.jgss,java.security.sasl,jdk.crypto.ec,jdk.unsupported,jdk.management,jdk.zipfs \
      --win-console; then
    # Windows 上刚生成的 exe 常被杀毒软件短暂占用，直接 mv 会报 Permission denied。
    # 重试几次就好 —— 这不是路径或权限配置的问题，别去改权限。
    for _ in 1 2 3 4 5; do
      mv "$RELEASE/$APP_BIN" "$APP_DIR" 2>/dev/null && break
      sleep 2
    done
    if [ ! -d "$APP_DIR" ]; then
      echo "jpackage 产物改名失败：$RELEASE/$APP_BIN 仍被占用" >&2
      exit 1
    fi
    cp "$ROOT/tools/demo/run-app.bat" "$APP_DIR/run.bat"
    cp "$ROOT/tools/demo/打开说明-app.txt" "$APP_DIR/打开说明.txt"
    python - "$APP_DIR" "$RELEASE/$APP_NAME.zip" <<'PY'
import sys, zipfile
from pathlib import Path
src, dst = Path(sys.argv[1]), Path(sys.argv[2])
with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for f in src.rglob("*"):
        if f.is_file():
            z.write(f, f.relative_to(src.parent))
print("wrote", dst)
PY
  else
    echo "jpackage 失败；保留 jar 演示包，免安装版未生成。" >&2
  fi
else
  echo "找不到 jpackage；跳过免安装版，只保留 jar 演示包。" >&2
fi
rm -rf "$RELEASE/_app_input"

echo "==> 6/6 生成源码 zip"
python - "$ROOT" "$RELEASE/食刻点餐-源码.zip" <<'PY'
import os, sys, zipfile
from pathlib import Path
root, dst = Path(sys.argv[1]), Path(sys.argv[2])
exclude_dirs = {
    "node_modules", "target", "dist", ".tooling", ".git", "release",
    ".tmp", "data", "__pycache__", "_shots", "_chrome_profile",
    "_verify_profile",
}
with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for current, dirs, files in os.walk(root):
        cur = Path(current)
        dirs[:] = [d for d in dirs if d not in exclude_dirs]
        # static 是打包时拷进去的构建产物，不是源码
        if cur == root / "server/src/main/resources" and "static" in dirs:
            dirs.remove("static")
        for name in files:
            if name == "_contact_sheet.jpg" or name.startswith("_probe_tmp"):
                continue
            f = cur / name
            z.write(f, f.relative_to(root))
print("wrote", dst)
PY

echo "==> 写发布说明"
# 论文大纲单独放一份在发布目录里：答辩时翻得最快的就是它，
# 不用去源码包里找。
cp "$ROOT/毕设选题与论文大纲.md" "$RELEASE/论文选题与大纲.md"
cat > "$RELEASE/说明.txt" <<EOF
食刻点餐 · 发布包说明
日期：$(date +%F)

一、免安装版（推荐）
文件：食刻点餐-免安装版.zip
1. 解压整个文件夹（不要在压缩包里直接双击）
2. 双击 run.bat
3. 浏览器会自动打开 http://127.0.0.1:8080/
   - 不需要安装 Java，不需要 MySQL（自带运行时和内嵌数据库）
   - 第一次启动需要 10-20 秒初始化演示数据
4. 演示账号：
   顾客   13800000621 / 123456
   管理员 13900000001 / admin123
   后厨   13900000002 / kitchen123
5. 数据保存在 data/ 文件夹；删除 data/ 再启动即可恢复初始数据。

二、体积更小（需要 Java 21）
文件：食刻点餐-演示.zip
- 解压后双击 run.bat
- 需要电脑已安装 Java 21；仍然不需要 MySQL
- 账号和免安装版相同

三、源码包
文件：食刻点餐-源码.zip
- 包含前端（web/）、后端（server/）、静态设计稿（demo/）、数据库结构、论文选题与大纲
- 不包含 node_modules / target / dist / .tooling / release
- 开发启动：
    bash tools/run_server.sh
    cd web && npm install && npm run dev
- 重新构建发布包：
    bash tools/build_demo_package.sh

四、常见问题
- 8080 端口被占用：免安装版可以这样换端口
    Shike.exe --server.port=8081
  然后访问 http://127.0.0.1:8081/
- 页面暂时打不开：等 10-20 秒刷新一次。
- 停止服务：关闭标题为 "Shike Service" 的窗口即可。

题目：基于 AHP-TOPSIS 与历史订单数据的个性化菜品推荐方法及系统实现
EOF

# static 属于构建产物。留着它下次 mvn package 会带上这一版前端，
# 改了 Vue 却忘了重新拷就会打出旧页面 —— 这类问题构建日志完全看不出来。
rm -rf "$STATIC"

echo
echo "完成。发布目录：$RELEASE"
ls -lh "$RELEASE" | sed 's/^/  /'
