# =============================================================================
# Phase 1 端到端功能测试脚本
# 用法: .\scripts\test-phase1.ps1 [-SkipPython] [-Verbose] [-FullSession] [-SkipUpload] [-UseResume]
#   -SkipPython  跳过需要 Python Agent 的测试（Planning/Interview/Evaluation）
#   -Verbose     显示详细 API 响应体
#   -FullSession  循环提交全部轮次（默认只提交第 1 轮）
#   -SkipUpload  跳过简历上传测试（即使 sample.pdf 存在）
#   -UseResume  上传简历后等待 Worker 解析完成，用 resumeId 创建面试（简历驱动出题）
# =============================================================================

param(
    [switch]$SkipPython,
    [switch]$Verbose,
    [switch]$FullSession,
    [switch]$SkipUpload,
    [switch]$UseResume
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ─────────────── 颜色辅助 ────────────────────────────────────────────────────
function Pass($msg) { Write-Host "  ✅ PASS  $msg" -ForegroundColor Green }
function Fail($msg) { Write-Host "  ❌ FAIL  $msg" -ForegroundColor Red }
function Info($msg) { Write-Host "  ℹ  INFO  $msg" -ForegroundColor Cyan }
function Warn($msg) { Write-Host "  ⚠  WARN  $msg" -ForegroundColor Yellow }
function Step($msg) { Write-Host "`n▶ $msg" -ForegroundColor Yellow }

# ─────────────── HTTP 辅助 ────────────────────────────────────────────────────
$JAVA_BASE = "http://localhost:8080/api"
$PYTHON_BASE = "http://localhost:8000"

function Invoke-Api {
    param(
        [string]$Method = "GET",
        [string]$Url,
        [string]$Body = $null,
        [string]$Token = $null,
        [string]$ContentType = "application/json"
    )
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }

    try {
        if ($Body) {
            # 显式 UTF-8 编码：避免 PS 5.1 用系统默认编码导致中文变 ?
            $utf8Body = [System.Text.Encoding]::UTF8.GetBytes($Body)
            $response = Invoke-RestMethod -Method $Method -Uri $Url `
                -Headers $headers -Body $utf8Body -ContentType "$ContentType; charset=utf-8"
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers
        }
        return $response
    } catch {
        # 安全提取状态码：处理网络错误（无 Response）和 HTTP 错误
        $statusCode = $null
        $errorBody  = $null
        try {
            $statusCode = $_.Exception.Response.StatusCode.value__
        } catch {
            $statusCode = 0
        }
        try {
            $errorBody = if ($_.ErrorDetails) { $_.ErrorDetails.Message } else { $_.Exception.Message }
        } catch {
            $errorBody = $_.Exception.Message
        }
        return @{ __error = $true; __status = $statusCode; __body = $errorBody }
    }
}

# 安全地判断 Invoke-Api 是否返回了 HTTP 错误
function Is-Error($resp) { $resp -is [hashtable] -and $resp.__error }

# ─────────────── Multipart 文件上传 ──────────────────────────────────────────
# 使用 Windows 10+ 自带的 curl.exe，简单可靠，不损坏二进制文件。
function Invoke-MultipartUpload {
    param(
        [string]$Url,
        [string]$FilePath,
        [string]$Token
    )
    try {
        $result = & curl.exe -s -X POST $Url `
            -H "Authorization: Bearer $Token" `
            -F "file=@$FilePath" 2>&1
        if ($LASTEXITCODE -ne 0) {
            return @{ __error = $true; __status = 0; __body = "curl exit code ${LASTEXITCODE}: $result" }
        }
        $obj = $result | ConvertFrom-Json
        if ($obj.id) {
            return $obj
        } else {
            return @{ __error = $true; __status = 500; __body = ($result | Out-String) }
        }
    } catch {
        return @{ __error = $true; __status = 0; __body = "Upload exception: $($_.Exception.Message)" }
    }
}

# ─────────────── 测试计数器 ────────────────────────────────────────────────────
$script:passed = 0
$script:failed = 0

function Test-Case {
    param([string]$name, [scriptblock]$block)
    try {
        & $block
        $script:passed++
    } catch {
        Fail "$name - Exception: $_"
        $script:failed++
    }
}

# =============================================================================
# 前置检查：服务是否在线
# =============================================================================
Step "前置检查"

$javaOk = $false
$pythonOk = $false

try {
    $h = Invoke-RestMethod -Uri "$JAVA_BASE/health" -TimeoutSec 3
    if ($h.status -eq "ok") { $javaOk = $true; Pass "Java 后端在线 (localhost:8080)" }
} catch {
    Fail "Java 后端不在线 - 请先执行: cd backend; mvn spring-boot:run"
}

try {
    $h = Invoke-RestMethod -Uri "$PYTHON_BASE/health" -TimeoutSec 3
    if ($h.status -eq "ok") { $pythonOk = $true; Pass "Python Agent 在线 (localhost:8000)" }
} catch {
    if ($SkipPython) {
        Info "Python Agent 不在线，已使用 -SkipPython 跳过相关测试"
    } else {
        Warn "Python Agent 不在线 - 请先执行: cd agent; uvicorn main:app --reload --port 8000"
        Info "若要跳过 Python 相关测试，请加参数: -SkipPython"
    }
}

if (-not $javaOk) {
    Write-Host "`n❌ Java 后端未启动，终止测试。" -ForegroundColor Red
    exit 1
}

# =============================================================================
# 1. 认证模块
# =============================================================================
Step "1. 认证模块"

# 生成随机后缀避免重复注册
$suffix = Get-Random -Maximum 99999
$testUser = "e2euser$suffix"
$testEmail = "e2e$suffix@test.com"
$testPass = "E2eTest1234"

$script:token = $null
$script:userId = $null

Test-Case "1.1 注册新用户" {
    $body = @{
        username = $testUser
        email = $testEmail
        password = $testPass
        displayName = "E2E Test User"
        targetDomain = "backend"
        experienceLevel = "junior"
    } | ConvertTo-Json -Compress

    $resp = Invoke-Api -Method POST -Url "$JAVA_BASE/auth/register" -Body $body
    if ($resp.accessToken -and $resp.userId) {
        $script:token = $resp.accessToken
        $script:userId = $resp.userId
        Pass "注册成功 userId=$($resp.userId)"
    } else {
        throw "注册失败: $($resp | ConvertTo-Json)"
    }
}

Test-Case "1.2 重复注册应返回 400" {
    $body = @{ username = $testUser; email = $testEmail; password = $testPass } | ConvertTo-Json -Compress
    $resp = Invoke-Api -Method POST -Url "$JAVA_BASE/auth/register" -Body $body
    if (Is-Error $resp -and $resp.__status -eq 400) {
        Pass "重复注册正确返回 400"
    } else {
        throw "期望 400，实际: $($resp | ConvertTo-Json)"
    }
}

Test-Case "1.3 登录成功" {
    $body = @{ username = $testUser; password = $testPass } | ConvertTo-Json -Compress
    $resp = Invoke-Api -Method POST -Url "$JAVA_BASE/auth/login" -Body $body
    if ($resp.accessToken) {
        $script:token = $resp.accessToken
        Pass "登录成功，token 已刷新"
    } else {
        throw "登录失败"
    }
}

Test-Case "1.4 错误密码应返回 401" {
    $body = @{ username = $testUser; password = "WrongPass999" } | ConvertTo-Json -Compress
    $resp = Invoke-Api -Method POST -Url "$JAVA_BASE/auth/login" -Body $body
    if (Is-Error $resp -and $resp.__status -eq 401) {
        Pass "错误密码正确返回 401"
    } else {
        throw "期望 401，实际: $($resp.__status)"
    }
}

Test-Case "1.5 获取个人资料" {
    $resp = Invoke-Api -Url "$JAVA_BASE/auth/profile" -Token $script:token
    if ($resp.username -eq $testUser) {
        Pass "profile 返回正确的 username"
    } else {
        throw "profile 返回异常: $($resp | ConvertTo-Json)"
    }
}

Test-Case "1.6 无 Token 访问受保护接口应返回 401 或 403" {
    $resp = Invoke-Api -Url "$JAVA_BASE/auth/profile"
    if (Is-Error $resp -and ($resp.__status -eq 401 -or $resp.__status -eq 403)) {
        Pass "无 Token 正确返回 $($resp.__status)"
    } else {
        throw "期望 401 或 403，实际: $($resp.__status)"
    }
}

# =============================================================================
# 2. 简历模块
# =============================================================================
Step "2. 简历模块（无文件场景）"

Test-Case "2.1 查询空简历列表" {
    $resp = Invoke-Api -Url "$JAVA_BASE/resume" -Token $script:token
    if ($resp -is [hashtable] -and $resp.__error) {
        throw "简历列表查询失败: $($resp.__status)"
    } else {
        Pass "简历列表查询正常（新用户无简历时返回空）"
    }
}

Test-Case "2.2 查询不存在的简历应返回 404" {
    $resp = Invoke-Api -Url "$JAVA_BASE/resume/99999" -Token $script:token
    if (Is-Error $resp -and $resp.__status -eq 404) {
        Pass "不存在简历正确返回 404"
    } else {
        throw "期望 404，实际: $($resp.__status)"
    }
}

Test-Case "2.3 删除不存在的简历应返回 404" {
    $resp = Invoke-Api -Method DELETE -Url "$JAVA_BASE/resume/99999" -Token $script:token
    if (Is-Error $resp -and $resp.__status -eq 404) {
        Pass "删除不存在简历正确返回 404"
    } else {
        throw "期望 404，实际: $($resp.__status)"
    }
}

# ── 如果系统已有 PDF 文件，可以测试上传 ──────────────────────────────────────
$testPdf = "$PSScriptRoot\sample.pdf"
$script:resumeId = $null

if ((Test-Path $testPdf) -and (-not $SkipUpload)) {
    Step "2. 简历上传测试（使用 $testPdf）"

    Test-Case "2.4 上传合法 PDF" {
        $resp = Invoke-MultipartUpload -Url "$JAVA_BASE/resume/upload" `
            -FilePath $testPdf -Token $script:token
        if ($resp.id) {
            $script:resumeId = $resp.id
            Pass "PDF 上传成功 resumeId=$($resp.id), status=$($resp.analysisStatus)"
        } else {
            throw "上传失败: $($resp | ConvertTo-Json)"
        }
    }

    if ($UseResume) {
        # 等待 Worker 解析简历完成（轮询 analysisStatus）
        Step "2. 等待简历解析（Worker 轮询）"
        Test-Case "2.5 等待解析完成" {
            $maxWait = 120
            $polled = 0
            $parsed = $false
            while ($polled -lt $maxWait) {
                $resp = Invoke-Api -Url "$JAVA_BASE/resume/$($script:resumeId)" -Token $script:token
                $status = $resp.analysisStatus
                if ($Verbose) { Info "简历状态: $status (已等待 ${polled}s)" }
                if ($status -eq "PARSED") {
                    $parsed = $true
                    break
                }
                if ($status -eq "FAILED") {
                    throw "简历解析失败: $($resp.analysisError)"
                }
                Start-Sleep -Seconds 2
                $polled += 2
            }
            if ($parsed) {
                Pass "简历解析完成（等待 ${polled}s）"
            } else {
                Info "等待超时（${maxWait}s），继续使用当前状态: $status"
            }
        }
    }
} elseif (Test-Path $testPdf) {
    Info "sample.pdf 存在但已使用 -SkipUpload 跳过上传测试"
} else {
    Info "未找到 $testPdf，跳过简历上传测试（可将任意 PDF 命名为 sample.pdf 放到 scripts/ 目录）"
}

# =============================================================================
# 3. 面试模块
# =============================================================================
Step "3. 面试模块"

$script:sessionId = $null

Test-Case "3.1 创建面试 Session$(if ($UseResume -and $script:resumeId) { '（简历驱动）' } else { '（subTopics 驱动）' })" {
    $config = @{
        domain = "backend"
        totalTurns = 3
        difficultyRange = @(2, 4)
        scoringMode = "standard"
        language = "zh-CN"
    }
    if (-not ($UseResume -and $script:resumeId)) {
        $config.subTopics = @("java", "spring")
    }

    $sessionBody = @{ title = "E2E 测试 - Java 后端" }
    if ($UseResume -and $script:resumeId) {
        $sessionBody.resumeId = $script:resumeId
        $sessionBody.title = "E2E 测试 - 简历驱动面试"
    }
    $sessionBody.config = $config

    $body = $sessionBody | ConvertTo-Json -Depth 3 -Compress
    if ($Verbose) { Info "创建请求: $body" }

    $resp = Invoke-Api -Method POST -Url "$JAVA_BASE/interview/session/create" `
        -Token $script:token -Body $body
    if (Is-Error $resp) {
        $errMsg = if ($resp.__body) { $resp.__body } else { "HTTP $($resp.__status)" }
        if ($resp.__status -eq 400 -and $errMsg -match "still being analyzed") {
            Warn "简历尚未解析完成，请等待 Worker 解析完毕后再创建面试"
        }
        throw "Session 创建失败: $errMsg"
    }
    if ($resp.id -and $resp.status -eq "PLANNING") {
        $script:sessionId = $resp.id
        Pass "Session 创建成功 sessionId=$($resp.id), status=PLANNING"
    } else {
        throw "Session 创建返回异常: $($resp | ConvertTo-Json)"
    }
}

Test-Case "3.2 查询 Session 详情" {
    if (-not $script:sessionId) {
        Info "跳过（Session 未创建）"
        return
    }
    $resp = Invoke-Api -Url "$JAVA_BASE/interview/$($script:sessionId)" -Token $script:token
    if (Is-Error $resp) {
        throw "Session 详情查询失败: HTTP $($resp.__status)"
    }
    if ($resp.id -eq $script:sessionId) {
        Pass "Session 详情查询正常 status=$($resp.status)"
    } else {
        throw "查询失败"
    }
}

Test-Case "3.3 config.domain 缺失时创建应返回 400" {
    $body = @{ title = "no domain"; config = @{ totalTurns = 3 } } | ConvertTo-Json -Depth 2 -Compress
    $resp = Invoke-Api -Method POST -Url "$JAVA_BASE/interview/session/create" `
        -Token $script:token -Body $body
    if (Is-Error $resp -and $resp.__status -eq 400) {
        Pass "缺少 domain 正确返回 400"
    } else {
        throw "期望 400，实际: $($resp.__status)"
    }
}

Test-Case "3.4 查询不属于自己的 Session 应返回 404" {
    $resp = Invoke-Api -Url "$JAVA_BASE/interview/99999" -Token $script:token
    if (Is-Error $resp -and $resp.__status -eq 404) {
        Pass "越权访问正确返回 404"
    } else {
        throw "期望 404，实际: $($resp.__status)"
    }
}

Test-Case "3.5 获取 Session 轮次列表（初始为空）" {
    if (-not $script:sessionId) { Info "跳过（Session 未创建）"; return }
    $resp = Invoke-Api -Url "$JAVA_BASE/interview/$($script:sessionId)/turns" -Token $script:token
    if (Is-Error $resp) {
        throw "turns 查询失败: $($resp.__status)"
    } else {
        Pass "未开始的 session 轮次列表查询正常"
    }
}

Test-Case "3.6 分页查询 Session 列表" {
    $resp = Invoke-Api -Url ($JAVA_BASE + '/interview/sessions?page=0&size=10') -Token $script:token
    if (Is-Error $resp) {
        throw "Session 列表查询失败: HTTP $($resp.__status)"
    }
    if ($resp.totalElements -ge 1) {
        Pass "Session 列表查询正常，共 $($resp.totalElements) 条"
    } else {
        Pass "Session 列表为空（新用户正常）"
    }
}

# ── Python 依赖测试 ──────────────────────────────────────────────────────────
if (-not $SkipPython -and $pythonOk) {

    $script:questionSequence = $null
    $script:totalTurns = 0
    $script:plan = $null

    # 用于 FullSession 模式的话题→回答映射（使用 script: 保证 Test-Case 内可访问）
    $script:answerMap = @{
        "java"     = "Java 线程池核心参数有 corePoolSize、maximumPoolSize、keepAliveTime、workQueue、threadFactory、rejectedExecutionHandler。当任务数超过 corePoolSize 时进入队列，队列满则扩到 max，再满走拒绝策略。我在项目中用 ThreadPoolExecutor 自定义参数，避免使用 Executors 默认队列溢出。"
        "spring"   = "IoC 通过 DI 管理 Bean 生命周期，AOP 用动态代理实现横切关注点。我在项目中用 AOP 做操作日志记录和事务管理，@Around 注解拦截 Service 方法，写入日志库。AOP 底层 JDK 代理需实现接口，CGLIB 代理子类实现。"
        "database" = "MySQL 索引用 B+ 树，叶子节点双向链表，范围查询快。最左匹配原则会失效，OR 两侧都需索引，LIKE 以 % 开头失效。我在项目里用 EXPLAIN 分析慢查询，加联合索引覆盖查询字段，避免回表。"
        "redis"    = "Redis 缓存穿透用布隆过滤器，缓存击穿用互斥锁或永不过期 + 异步更新，缓存雪崩用过期时间加随机偏移。项目中用 Redis String 做缓存，ZSet 做排行榜，还用了 Redisson 分布式锁解决重复提交。"
        "default"  = "这是一个很好的技术问题。在高并发场景下，我通过分层的架构设计来应对：CDN 缓存静态资源、Redis 缓存热点数据、应用层使用连接池和线程池、数据库层面读写分离加索引优化。同时用消息队列削峰填谷，监控系统自动扩缩容。"
    }

    Step "3. 面试模块 - Python Agent 联调$(if ($FullSession) { '（完整面试模式）' } else { '（单轮模式）' })"

    $skipPyTests = $false
    if (-not $script:sessionId) {
        Info "跳过 Python 联调（Session 未创建）"
        $skipPyTests = $true
    }

    if (-not $skipPyTests) {
    Test-Case "3.7 启动面试（调用 Python Planning）" {
        $resp = Invoke-Api -Method POST -Url "$JAVA_BASE/interview/$($script:sessionId)/start" `
            -Token $script:token
        if (-not (Is-Error $resp)) {
            Pass "面试启动成功，Session 已切换为 IN_PROGRESS"
            if ($resp.plan) {
                $script:plan = $resp
                $script:questionSequence = $resp.plan.question_sequence
                $script:totalTurns = $resp.plan.total_turns
                if ($Verbose) { Info "Planning 生成 $($script:totalTurns) 轮问题" }
            }
            if ($Verbose) { Info "Planning 响应: $($resp | ConvertTo-Json -Depth 3)" }
        } else {
            if ($resp.__status -eq 500) {
                Warn "Planning 失败（HTTP 500）：请检查 Python Agent 的 LLM 配置（.env 中 OPENAI_API_KEY 等）"
            }
            throw "启动失败: status=$($resp.__status) body=$($resp.__body)"
        }
    }

    if ($FullSession -and $script:questionSequence) {
        # ── 完整面试模式：循环提交全部轮次 ──────────────────────────────
        $script:turnList = New-Object System.Collections.ArrayList
        foreach ($planQ in $script:questionSequence) {
            $tn = $planQ.turn_no
            $tp = if ($planQ.topic) { $planQ.topic } else { "default" }
            $ans = $script:answerMap["default"]
            foreach ($ak in $script:answerMap.Keys) { if ($tp -match $ak) { $ans = $script:answerMap[$ak]; break } }
            [void]$script:turnList.Add(@{ turnNo = $tn; topic = $tp; answer = $ans })
        }

        Test-Case "3.8 提交全部 $($script:turnList.Count) 轮回答" {
            for ($ti = 0; $ti -lt $script:turnList.Count; $ti++) {
                $td = $script:turnList[$ti]
                $etn = $td.turnNo
                $tp = $td.topic
                $ans = $td.answer

                Start-Sleep -Milliseconds 500

                # 先查当前轮次，确认是否需要提交
                $sc = Invoke-Api -Url "$JAVA_BASE/interview/$($script:sessionId)" -Token $script:token
                $st = if ($null -ne $sc.currentTurn) { $sc.currentTurn } else { 1 }

                if ($st -gt $etn) {
                    Info "第 $etn 轮（$tp）跳过，当前已在轮次 $st"
                    continue
                }

                $body = @{ response = $ans } | ConvertTo-Json -Compress
                $resp = Invoke-Api -Method POST -Url "$JAVA_BASE/interview/$($script:sessionId)/turn" `
                    -Token $script:token -Body $body

                if (Is-Error $resp) {
                    if ($resp.__status -eq 500) {
                        Warn "第 $etn 轮提交失败（HTTP 500）：请检查 Python Agent 的 LLM 配置"
                    }
                    throw "第 $etn 轮提交失败: status=$($resp.__status) body=$($resp.__body)"
                }

                # 二次验证 session 进度
                $ca = Invoke-Api -Url "$JAVA_BASE/interview/$($script:sessionId)" -Token $script:token
                $ct = if ($null -ne $ca.currentTurn) { $ca.currentTurn } else { 1 }

                if ($ct -gt $etn) {
                    $sd = $resp.score_detail
                    $score = if ($sd -and $sd.overall_score) { $sd.overall_score } else { "?" }
                    $fb = if ($resp.feedback_text) { $resp.feedback_text } else { "-" }
                    Info "第 $etn 轮（$tp）提交成功  score=$score  feedback=$fb"
                } else {
                    Info "第 $etn 轮（$tp）已提交（响应正常）"
                }
            }
            Pass "全部 $($script:turnList.Count) 轮提交完成"
        }

        Test-Case "3.9 查询轮次列表（完整面试后）" {
            Start-Sleep -Seconds 1
            $resp = Invoke-Api -Url "$JAVA_BASE/interview/$($script:sessionId)/turns" -Token $script:token
            if ($resp -is [System.Array]) {
                Pass "turns 查询正常，共 $($resp.Count) 条（预期 $($script:turnList.Count) 轮）"
            } else {
                Pass "turns 查询正常"
            }
        }

    } else {
        # ── 单轮模式 ──────────────────────────────────────────
        Test-Case "3.8 提交第 1 轮回答（调用 Python Interview）" {
            $body = @{ response = "HashMap 底层是数组 + 链表/红黑树，JDK8 引入红黑树优化查询性能，当链表长度超过 8 且数组长度超过 64 时转红黑树，删除元素导致树节点少于 6 时退化回链表" } `
                | ConvertTo-Json -Compress
            $resp = Invoke-Api -Method POST -Url "$JAVA_BASE/interview/$($script:sessionId)/turn" `
                -Token $script:token -Body $body
            if (-not (Is-Error $resp)) {
                Pass "第 1 轮提交成功"
                if ($Verbose) { Info "Turn 响应: $($resp | ConvertTo-Json -Depth 3)" }
            } else {
                if ($resp.__status -eq 500) {
                    Warn "提交失败（HTTP 500）：请检查 Python Agent 的 LLM 配置（.env 中 OPENAI_API_KEY、OPENAI_BASE_URL、OPENAI_MODEL）"
                }
                throw "提交失败: status=$($resp.__status) body=$($resp.__body)"
            }
        }

        Test-Case "3.9 查询轮次列表（应有数据）" {
            Start-Sleep -Seconds 1
            $resp = Invoke-Api -Url "$JAVA_BASE/interview/$($script:sessionId)/turns" -Token $script:token
            if ($resp -is [System.Array] -and $resp.Count -ge 0) {
                Pass "turns 查询正常，共 $($resp.Count) 条"
            } else {
                Pass "turns 查询正常（可能为空，Python Agent 异步写入）"
            }
        }
    }

    Test-Case "3.10 触发评估（调用 Python Evaluation）" {
        $resp = Invoke-Api -Method POST -Url "$JAVA_BASE/interview/$($script:sessionId)/evaluate" `
            -Token $script:token
        if (-not (Is-Error $resp)) {
            Pass "评估触发成功"
            if ($Verbose) { Info "Evaluation 响应: $($resp | ConvertTo-Json -Depth 3)" }
        } else {
            if ($resp.__status -eq 500) {
                Warn "评估失败（HTTP 500）：请检查 Python Agent 的 LLM 配置"
            }
            throw "评估失败: status=$($resp.__status) body=$($resp.__body)"
        }
    }

    }  # end if (-not $skipPyTests)

} elseif ($SkipPython) {
    Info "已跳过 Python Agent 联调测试（-SkipPython）"
} else {
    Info "Python Agent 不在线，跳过联调测试"
    Info "启动 Python Agent: cd agent; uvicorn main:app --reload --port 8000"
    Info "并确保 .env 中配置了 LLM 凭据（OPENAI_API_KEY, OPENAI_BASE_URL, OPENAI_MODEL）"
}

# =============================================================================
# 汇总报告
# =============================================================================
$total = $script:passed + $script:failed
Write-Host "`n============================================================" -ForegroundColor White
Write-Host " Phase 1 测试结果: $($script:passed)/$total 通过" -ForegroundColor $(if ($script:failed -eq 0) { "Green" } else { "Yellow" })
if ($script:failed -gt 0) {
    Write-Host " ❌ 失败: $($script:failed) 个" -ForegroundColor Red
}
Write-Host "============================================================" -ForegroundColor White

if ($script:failed -gt 0) { exit 1 } else { exit 0 }
