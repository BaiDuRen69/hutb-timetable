# 湖南工商大学教室课表查询 App

一个只读的课表查询工具：个人课表、按教学楼/教室查询、空教室筛选、离线缓存。
数据来自学校教务系统（强智 jsxsd）的公开查询页面，App 不做任何写操作。

## 功能与实现状态

| 需求 | 状态 | 位置 |
|---|---|---|
| 周视图网格（周一~周日 × 节次） | ✅ | `WeekGrid.kt` |
| 点击课程弹出详情 | ✅ | `CourseDetailDialog.kt` |
| 周次切换（左右箭头 + 弹窗选择） | ✅ | `TimetableViewModel.switchWeek` |
| 上下学期切换 | ✅ | `TimetableViewModel.switchSemester` |
| 登录界面（WebView 承载 CAS） | ✅ | `LoginScreen.kt` / `WebLoginActivity.kt` |
| 本地缓存 + 离线查看 | ✅ | `ScheduleCache.kt` / `TimetableRepository` |
| 网络异常 / 登录失效提示 | ✅ | `HutbError` / `EduHttpClient.isLoginRequired` |
| **按教学楼/教室查询** | ✅ | `RoomPickerDialog.kt` + `parseRoomSchedule()` + `RoomMatrix.kt` |
| 校区/教学楼/教室三级级联 | ✅ | `TimetableViewModel.loadCampuses/loadBuildings/loadClassrooms` |
| 按某一天查询 | ⚠️ | 已按"周视图 + 周次切换"实现，未做独立单日视图 |
| **星期 / 节次区间筛选** | ✅ | `ScheduleFilter.kt` + `FilterSheet.kt`（客户端过滤，见 `docs/protocol-facts.md` 1.6.3） |
| **精简教室名显示** | ✅ | `shortRoomName()`（core）+ `RoomMatrix` 左列 / 空教室列表（`乐知楼E101*(智慧)` → `E101`，星号与括号注记都剥掉；左列宽度按最长教室名自适应） |
| **一键筛选空教室** | ✅ | `TimetableScreen` 工具条"只看空教室"（教室清单 − 当前筛选范围内有课的教室） |
| **教室行排序切换** | ✅ | `RoomOrdering.kt`（core）+ 工具条排序菜单：楼栋优先（E101→E102→E201）、编号优先（E101→F101→E102）、楼层优先（1F 全部 → 2F 全部），选择持久化 |
| **矩阵展示整栋楼全部教室** | ✅ | 行 = 有课教室 ∪ 教学楼教室清单（`RoomMatrix.allRooms`）：按星期/节次筛选后，当前时段没课的教室仍保留为空行；`roomIdentity()` 统一全/半角注记写法，避免同一教室重复成两行 |
| **记住校区 / 教学楼 / 教室选择** | ✅ | `AppPrefs.lastCampusId/…` + `TimetableViewModel.restoreRemembered*`（下次进教室模式自动恢复） |
| **设置页：当前周次 + 主题 + 作息时间表** | ✅ | `AppSettings.kt` + `SettingsScreen.kt`（只需选“今天是开学第几周”，自动反推第一周周一并按学期保存；深浅模式 + 5 套主题配色；内置夏/冬时制 × 南/北校区四套作息，可改可加自定义） |
| **登录与离线策略** | ✅ | 首次安装必须登录；之后启动未登录只提示、可查看上次登录账号的离线个人课表；教室课表必须登录后才能进入（未登录点“教室课表”会拉起登录） |
| **账号隐私** | ✅ | 退出登录可选“保留离线课表”或“清除并退出”；离线课表只读上次登录账号的哈希分区，登录页禁止截图（FLAG_SECURE） |
| **支持作者** | ✅ | 设置页“请我喝杯咖啡”：支付宝 / 微信收款码，Android 10+ 保存到相册、旧版本走系统文件选择器，无需存储权限；收款码已降采样 |
| **可访问性** | ✅ | 教室矩阵在开启 TalkBack 时为可见课程卡叠加语义/点击节点，逐卡播报“周三第1-2节 课程 教室 教师” |
| **页面过渡动画** | ✅ | 登录页/设置页覆盖层使用 `AnimatedVisibility` 淡入 + 滑动过渡 |
| **教室矩阵粘性表头** | ✅ | `RoomMatrix` 固定星期/节次表头 + `LazyColumn` 行列表；每行网格/课程卡由单个 `Canvas` 绘制（文字布局缓存），表头与所有行共享横向偏移 + `graphicsLayer` 平移，纵向滚动时不再反复组合/测量大量 Text 节点 |
| **连堂课合并大格子** | ✅ | `RoomMatrix` 起始格拉宽卡片横跨整个节次区间，不再是"课程卡 + 连堂续格" |
| **登录页可返回 / 免重复登录** | ✅ | 已登录时右上角账号按钮打开登录页可返回，支持退出登录；移除无用的学号输入与"清除提示" |
| JSON API 加速路径 | ⚠️ | 课表查询不走 JSON API（本校 `app.do` 实测全部要求登录态且返回无数据）；仅学期列表优先尝试 `getXnxq`，失败回退 HTML |

### 教室查询的使用流程

1. 顶部切到「教室课表」→ GET `kbxx_classroom` 解析 `#xqid` 得到校区列表
   （此时**不发课表查询**：没选教学楼时服务端会把全校 350 间教室都返回来）
2. 默认选中第一个校区 → 加载该校区教学楼（`#jzwid` 或 `getJxlByAjax`）
3. 点黄色筛选条 → 选校区 → 选教学楼 → 选教室（可"全部教室"）
   —— 选完教学楼时，App 已经在拉该楼的课表，**教室清单由表格首列反推**，
   并按教学楼名收窄（服务端会把同校区的别的场馆顺带带出来）
4. 提交 `xnxqh` + `jzwid` + `xqid` + `zc1/zc2` 到 `kbxx_classroom_ifr`
   （`jzwid` 单独就能定位教学楼，`xqid` 在有 `jzwid` 时被服务端忽略，两个都带即可）
5. 用 `parseRoomSchedule` 解析（行=场地、列=星期），教室信息取自行首；
   指定了教室就**在客户端按教室名过滤**；连堂课（服务端每格都写一遍内容）会合并成一次课
6. 结果用**矩阵**展示（行=教室、列=星期 × 节次对），与教务系统页面一致，见 `docs/protocol-facts.md` 1.7.1；
   标题栏显示「校区 教学楼 教室」

> 教室课表与个人课表的缓存用 `scope` 字段隔离（`room|校区|教学楼|教室`），互不覆盖。


## 构建与安装

```bash
# Git Bash
export JAVA_HOME=<JDK 21 路径>
export ANDROID_HOME=<Android SDK 路径>
./gradlew :app:assembleDebug --console=plain

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

根目录的 `课表查询-debug.apk` 是最新 debug 包的副本，方便直接拷到手机安装。
release 包使用独立签名，和 debug 包不能覆盖安装；从 debug 换到 release 需要先卸载一次（会清空本地缓存和会话）。

## 测试

```bash
./gradlew :core:unitTest --console=plain
# → JUnit version 4.13.2 / OK (107 tests)
```

测试类自动发现，新增 `*Test.kt` 无需修改构建脚本（细节见 `docs/development.md`）。

## 隐私与合规

* 密码在**统一认证页面（WebView）内输入**：App 进程内完成渲染但不存储、不上传密码，
  登录完成后只保留会话 Cookie（JSESSIONID / SERVERID，加密落盘）。
* App 不上传任何数据到第三方服务器；备份/换机迁移默认排除全部本地数据
  （`dataExtractionRules`），防止会话凭据随云备份外泄。
* 仅访问学校教务系统官方地址进行只读查询，不做任何选课/成绩写入操作。
* 已知风险：教务系统与统一认证目前仅提供 HTTP 明文访问（学校侧限制），
  会话 Cookie 在链路上为明文传输，App 侧已将放行范围收紧到这两个主机。
* 请遵守学校教务系统的使用规定。

---

## 文档

- [教务系统协议实测事实](docs/protocol-facts.md)：查询路径、参数、页面结构与解析约定
- [工程结构与开发说明](docs/development.md)：模块划分、技术选型、测试方案与已知限制
