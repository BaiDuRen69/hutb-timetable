# 工程结构与开发说明

## 工程结构

手写文件树在项目变动时必然腐烂（已两次过期），这里不再维护逐文件清单，
以仓库实际目录为准：

- `core/src/main/java/com/hutb/timetable/core/` —— 领域模型、解析器与纯逻辑（不依赖 Android）
- `core/src/test/java/com/hutb/timetable/core/` —— 单元测试（`*Test.kt` 自动发现）
- `app/src/main/java/com/hutb/timetable/`
  - `data/remote/`：OkHttp 封装、教务接口、页面快照
  - `data/local/`：课表缓存、凭据（`CredentialStore`）、偏好（`AppPrefs`）、选项缓存
  - `data/repo/`：网络 + 缓存编排
  - `ui/timetable/`、`ui/login/`、`ui/settings/`、`ui/theme/`
- `docs/`：`protocol-facts.md`（协议实测事实）、`development.md`（本文件）

### 技术选型

| 层 | 选型 | 理由 |
|---|---|---|
| UI | Jetpack Compose + Material3 | 课表网格需要大量自定义布局 |
| 网络 | OkHttp 4.12 | CookieJar 机制天然适合会话保持 |
| 解析 | Jsoup 1.18 | 强智课表是服务端渲染 HTML |
| 缓存 | 原生 SQLiteOpenHelper | 见下方「为什么没用 Room」 |
| 凭据 | EncryptedSharedPreferences | 仅 4 个敏感键加密落盘；偏好由 `AppPrefs` 普通存储 |
| 异步 | Coroutines | — |
| 依赖注入 | 手写单例 | 规模不需要 Hilt，减少构建复杂度 |
| 逻辑分层 | `:core` 纯 JVM 模块 | 解析/周次/排序/TTL 等纯逻辑可脱离 Android 单测 |

### 为什么没用 Room

AGP 9.2 已内置 Kotlin 支持（`android.builtInKotlin` 默认 true），
而 **KSP 与 AGP 内置 Kotlin 不兼容**：

```
KSP is not compatible with Android Gradle Plugin's built-in Kotlin
```

尝试过 `android.builtInKotlin=false` + KGP 2.2.21，但 AGP 9 移除了 `BaseExtension`，
报 `ApplicationExtensionImpl cannot be cast to BaseExtension`。

最终改为**手写 `SQLiteOpenHelper`**（`ScheduleCache.kt`），
既避开注解处理器，又保留了结构化存储。缓存表主键为
`(semester_id, week, scope)`，`scope` 区分个人课表与教室课表，互不覆盖。

### ⚠️ 中文路径下的单元测试

本项目位于中文路径 `D:\腾讯\WorkBuddy空间\课表查询`，
**Gradle 9.4.1 的 Test 任务通过独立 worker 进程加载测试类，worker 无法处理非 ASCII 路径**：

```
Could not execute test class 'com.hutb.timetable.core.JsxsdScheduleParserTest'
> ClassNotFoundException
```

对照实验：同一份 `:core` 模块代码复制到纯 ASCII 路径（`/tmp/coretest`）后，
7 个测试全部 PASSED —— 确认根因是路径而非代码。

**规避方案**：`core/build.gradle` 中禁用标准 `test` 任务，
改用 `JavaExec` 在 Gradle 进程内直接调用 `org.junit.runner.JUnitCore`。
测试类改为**自动扫描** `build/classes/kotlin/test` 下的 `*Test.class`，
新增测试文件无需再手改 `build.gradle`（漏改名单曾导致新测试静默不执行）。

```groovy
tasks.named('test') { enabled = false; dependsOn tasks.named('unitTest') }

tasks.register('unitTest', JavaExec) {
    dependsOn tasks.named('testClasses')
    mainClass = 'org.junit.runner.JUnitCore'
    classpath = files(...) + configurations.testRuntimeClasspath
    doFirst {
        // 扫描 *Test.class → setArgs(discovered)
    }
    ignoreExitValue = true
    doLast { /* 非 0 退出码抛异常 */ }
}
```

运行方式：

```bash
gradle :core:unitTest --console=plain
# 输出：JUnit version 4.13.2 / OK (104 tests)
```

> 注意：Gradle 9 的 `JavaExec` **没有公开的 `argsProvider` setter**，
> 直接写 `argsProvider = args` 会报
> `Could not set unknown property 'argsProvider'`。用 `args 'xxx'` 即可。

---

## 运行与调试

### 3.1 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 21（本机：`C:\Users\bai\.workbuddy\binaries\java\jdk-21.0.12.1+1`） |
| Gradle | 9.4.1 |
| AGP | 9.2.1 |
| compileSdk / targetSdk | 36 |
| minSdk | 24 |
| Kotlin | 2.2.20 |

### 3.2 构建

```bash
# Git Bash
export JAVA_HOME="C:\Users\bai\.workbuddy\binaries\java\jdk-21.0.12.1+1"
export ANDROID_HOME="C:\Users\bai\AppData\Local\Android\Sdk"
gradle :app:assembleDebug --console=plain
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

用 Android Studio 打开时，确认 `local.properties` 中 `sdk.dir` 指向本机 SDK。

### 3.2.1 跑单元测试

```bash
gradle :core:unitTest --console=plain
# → JUnit version 4.13.2 / OK (104 tests)
```

（中文路径下的注意事项见上文「⚠️ 中文路径下的单元测试」）

### 3.3 安装与运行

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.hutb.timetable/.ui.MainActivity
```

### 3.4 调试要点

**看网络请求**（OkHttp 日志已接入 `HttpLoggingInterceptor`，BuildConfig.DEBUG 下开启 BASIC 请求行日志）：

```bash
adb logcat -s okhttp.OkHttpClient:V
```

**看解析结果**：如果课表空白但请求成功，说明解析器没匹配到表格结构。
此时用下面的「页面快照」功能导出真实 HTML 对比。

**确认登录态**：登录后 `adb shell run-as com.hutb.timetable` 或用 App 内日志查看
`JSESSIONID` 是否写入 `hutb_secure_prefs`。

### 3.4.1 页面快照（教务系统改版排查的标准流程）

教务系统随时可能改版。App 在 **debug 包**里会自动把每次抓到的页面存到私有目录，
顶栏的 🐞 按钮（contentDescription = "页面快照"）可以查看已保存的文件。

#### 方式一：复制结构指纹（推荐，30 秒搞定）

1. 登录后点右上角 🐞 → 打开「页面快照」
2. 点最新那份快照，**结构指纹**会显示在下方
3. 点「**复制指纹**」→ 粘贴到聊天窗口发我

指纹只有 2~4 KB，包含表单字段、下拉选项、表格表头、候选接口路径、学期线索，
足够我精准改解析器，**不用传输上百 KB 的 HTML**。

> 「复制全部」会把所有快照的指纹一次性拼在一起，
> 如果有多个来源页面对比，用这个更省事。

#### 方式二：导出原始 HTML（需要完整页面时）

```bash
# 1. 列出已保存的快照
adb shell run-as com.hutb.timetable ls files/snapshots

# 2. 取回指定快照到当前目录（文件名换成上一步看到的）
adb shell run-as com.hutb.timetable cat files/snapshots/semesters_20261022_193800_042.html > real.html

# 3. 本地查看结构
grep -oE "<select[^>]*>" real.html
```

> **真机 + Windows 的坑**：`adb` 不在 PATH 时要用完整路径
> `C:\Users\bai\AppData\Local\Android\Sdk\platform-tools\adb.exe`；
> Git Bash 下路径会被 MSYS 转换，需要 `export MSYS_NO_PATHCONV=1`。

> **取回二进制文件的坑**：`run-as ... cat` 经过 Git Bash 的管道会按文本模式处理，
> 二进制内容（Cookie 数据库等）会被破坏，出现 "database disk image is malformed"。
> 要原样落盘就用 `adb exec-out run-as <pkg> cat <file> > out.bin`。
>
> 快照文件名带**毫秒**（`yyyyMMdd_HHmmss_SSS`），同一秒内多次抓取不会互相覆盖。

快照文件头部会写明来源 URL、抓取时间与**结构指纹**，对照起来很清楚：

```
<!-- PageSnapshot
     tag: semesters
     url: http://jwgl.hutb.edu.cn/jsxsd/framework/xsMain.jsp
     time: 20261022_193800
     size: 24567
     ---- 结构指纹 ----
【页面结构指纹】
标题：湖南工商大学教务一体化服务平台
长度：24567 字符
...
-->
```

> 快照只在 debug 包启用（`BuildConfig.DEBUG` 判断），release 包不会在用户设备上残留 HTML。
> 保留策略：最多 10 份，超出自动删除最旧的。

### 3.5 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| **页面结构已改变，解析失败** | 教务系统改版，下拉框/表格结构变了 | 用「页面快照 → 复制指纹」（3.4.1 节）把结构发我；学期列表已做九策略兼容 + JSON API 兜底 |
| **点登录按钮闪退** | `AppCompatActivity` 配了非 AppCompat 主题，`setContentView` 抛 `IllegalStateException: You need to use a Theme.AppCompat theme` | 本工程已修：登录页改为继承 `ComponentActivity`。若自行改回 AppCompatActivity，主题必须继承 `Theme.AppCompat.*` |
| 登录页反复跳转 | CAS 会话未建立 / 滑块未通过 | 在 WebView 中手动完成滑块 |
| 课表空白 | 表格选择器未命中 | 用 3.4.1 的方法导出指纹或 HTML，调整 `TABLE_SELECTORS` |
| 教室课表空白 | 该教学楼当周确实没有教室课表（非教室类场馆如体育馆/图书馆/食堂恒为空表），或表格行首语义判定错 | 看 logcat 的「教室由课表首列反推：原始 N 个」；导出 `kbxx_classroom` 的指纹核对表格形态 |
| 教室课表里同一门课重复出现 | 连堂课被服务端写进了它跨到的每一格 | 已修：整格文本相同的相邻格并成一次课（注意课程名+教师相同但班级不同的**不是**连堂） |
| 查整栋楼时课程互相覆盖 | 用的是个人课表那种"星期 × 节次"网格 | 已修：教室模式改用矩阵（行=教室，列=星期 × 节次对），见 1.7.1 |
| 教室列表为空 | 该教学楼当周没有课，或课表页没返回表格 | 确认教学楼选对；查 logcat 是否有「教室由课表首列反推，共 N 个」 |
| **教室列表显示成教学楼名** | 教室清单误用了 `getJxlByAjax` 的返回值 | 已修：教室清单改为从课表表格首列反推（`parseRoomLabels`） |
| 换了教学楼但课表没变 | 对话框的中间回调已改过 ViewModel 状态，"确定"时比对不出变化 | 已修：教室模式下"确定"后统一重新查一次 |
| 提示"登录已失效" | JSESSIONID 过期 | 点"重新登录"重新走 CAS |
| 网络错误 | 校园网限制 / 服务器维护 | 确认 `jwgl.hutb.edu.cn` 可达；离线时看缓存 |
| 明文流量被拦 | network_security_config 未生效 | 确认域名在 `hutb.edu.cn` 白名单内 |
| 顶部内容被状态栏遮住 | targetSdk 35+ 强制 edge-to-edge | 用 `ViewCompat.setOnApplyWindowInsetsListener` 把 inset 转成 padding（登录页已实现） |
| **构建报 `jlink executable ... does not exist`** | 用的 JDK 是精简版（PyCharm 的 JBR 是 `nomod`，没有 jlink） | 换完整 JDK：AGP 需要 jlink 生成 android-36 系统镜像 |
| **构建报 `JAVA_HOME is set to an invalid directory`** | `D:\programme\JDK` 已被移除/移动 | 指向一个完整 JDK，例如 `D:\tools\jdk-21.0.2` |

> **崩溃日志抓取**：`adb logcat -c` 清空 → 复现 → `adb logcat -d | grep -A 40 "FATAL EXCEPTION"`，
> `Caused by:` 那行才是真正的根因（上层 `Unable to start activity` 只是包装）。

---

---

## 发布签名

- Release 签名读取仓库外的 `keystore.properties`（已 gitignore），
  对应 `keystore/hutb-release.jks`；**这两个文件必须自行备份**，丢失后无法覆盖升级。
- `keystore.properties` 缺失时 release 退回 debug 签名，仅供本机验证，不能对外分发。
- release 构建开启 R8 与资源压缩（19 MB → 约 1.9 MB）。

## 已知限制

1. **中文路径下的标准 Gradle Test 任务不可用**：本项目位于中文路径，
   Gradle 9.4.1 的 Test worker 无法加载非 ASCII 路径下的测试类。
   当前用 `unitTest`（JavaExec + JUnitCore + 自动发现）规避；
   迁到 ASCII 路径后可恢复标准 `test` 任务。
2. **`androidx.security:security-crypto` 已被官方标记 deprecated**：
   仍用于凭据加密落盘（`CredentialStore`，版本见 `app/build.gradle`）。
   已实现的处置：KeyStore 不可用时降级为普通 SharedPreferences，
   通过 `secureStorage` 在登录页/设置页明确提示；凭据与偏好已拆分，
   `clearCredentials()` 与 `AppPrefs.clearPreferences()` 互不影响。
   后续再评估迁移到 DataStore + 自实现密钥派生。
3. **release 签名与 R8 未配置**：目前只产出 debug APK，未做混淆与正式签名。
4. **根目录 `课表查询-debug.apk` 是构建产物的副本**，用于拷贝到手机测试，
   与 `app/build/outputs/apk/debug/app-debug.apk` 内容一致；两处并存是有意为之。

## 待确认事项

1. **教师/班级字段的实际排布**：当前按"首行=课程名，其余按语义归位"的启发式。
   若某个字段缺失（如没有班级列），解析器需要相应放宽。
   无法归位的行会进入 `WeekSchedule.parseIssues`，debug 包顶栏显示"解析提示 N 行"。

> **已确认**：`/app.do` JSON API 对课表查询不可用（全部 method 要求登录态且返回无数据），
> 仅学期列表优先尝试 `getXnxq`，失败回退 HTML 解析。
>
> **已确认**：`class_query` 接口在本校部署不存在（返回「提示：非法访问！」），
> 教室清单改由课表表格首列反推（南校区 13 栋、月亮岛 3 栋，教室名均为真实教室）。
