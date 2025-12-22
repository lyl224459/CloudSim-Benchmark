#!/bin/bash
# CloudSim-Benchmark 运行脚本 (Linux/macOS)
# 用法: ./run.sh [batch|realtime] [algorithms] [randomSeed] [runs]

# 设置编码
export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"

# 检查 JAR 文件是否存在
if [ ! -f "build/libs/cloudsim-benchmark-1.0.0-all.jar" ]; then
    echo "错误: 找不到 JAR 文件，请先运行 ./gradlew fatJar 构建项目"
    exit 1
fi

# 构建参数
ARGS="$@"

# 运行程序
echo "========================================"
echo "CloudSim-Benchmark 实验平台"
echo "========================================"
echo ""

java -Dfile.encoding=UTF-8 -jar build/libs/cloudsim-benchmark-1.0.0-all.jar $ARGS

# 检查执行结果
if [ $? -ne 0 ]; then
    echo ""
    echo "程序执行出错"
    exit 1
fi

echo ""
echo "========================================"
echo "实验完成！结果已保存到 results/ 文件夹"
echo "========================================"

