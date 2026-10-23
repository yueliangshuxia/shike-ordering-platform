#!/usr/bin/env bash
# 启动后端。
#
# 为什么不用 mvn spring-boot:run：
#   本仓库的路径里有中文（E:\桌面\毕业设计点餐系统），而 spring-boot:run
#   给子进程构造 classpath 时走的是 MANIFEST 的 Class-Path 那一套，那份规范
#   只允许 ASCII。结果就是启动时报
#       ClassNotFoundException: com.shike.ordering.OrderingApplication
#   —— 类明明编译出来了，java -cp 直接跑也好好的，只有走这个插件不行。
#
#   所以这里绕开插件：自己拼 classpath，用 java 直接起。
#   顺带还快一些，不用每次重新打那个 46MB 的 fat jar。
#
# 用法：
#   bash tools/run_server.sh                    # 前台启动，Ctrl+C 停
#   bash tools/run_server.sh --server.port=9090 # 透传给 Spring Boot 的参数
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MVN="$ROOT/.tooling/apache-maven-3.9.9/bin/mvn"
SERVER="$ROOT/server"

if [ ! -x "$MVN" ]; then
  echo "找不到 Maven：$MVN" >&2
  echo "这台机器没装 Maven，仓库里自带的那份应当在这个位置。" >&2
  exit 1
fi

cd "$SERVER"

# 每次都 clean 再 compile，不是多余。
# VS Code 的 Java 扩展和 Maven 共用 target/，你改了一个实体类、
# IDE 在你改到一半时编译了一版，那个「半成品」class 就会留在 target/classes 里，
# 而 Maven 的增量编译看时间戳认为它是最新的，不会覆盖。
# 症状是启动后报 `java.lang.Error: Unresolved compilation problem`，
# 指向一个你明明已经改对的方法 —— 查半天查不出来。
# 多花几秒换掉这个坑，值。
echo "==> 编译（先 clean，避开 IDE 留下的半成品 class）"
"$MVN" -B -q clean compile

echo "==> 解析依赖"
"$MVN" -B -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt

if [ ! -s target/cp.txt ]; then
  echo "依赖列表没生成出来，target/cp.txt 是空的" >&2
  exit 1
fi

echo "==> 启动（MySQL 需要是运行状态）"
exec java -cp "target/classes;$(cat target/cp.txt)" \
  com.shike.ordering.OrderingApplication "$@"
