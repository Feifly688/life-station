# 安全规范（Security Policy）

适用范围：`Feifly688/life-station`（翡栖 / `com.feiqi`）的**源码、构建产物、签名密钥与发布流程**。
本文档规定必须采取的安全措施与操作规范，配套的自动化加固已于 2026-09-21 落地（见 §1）。

---

## 1. 当前安全基线（已体检）

| 项 | 现状 | 结论 |
| --- | --- | --- |
| 仓库可见性 | `public` | ⚠️ 源码与 Release 附件**全球可读**：任何敏感信息一旦入库即等同公开 |
| 协作者 | 仅 `Feifly688`（admin） | ✅ 权限面最小 |
| 密钥扫描 | Secret scanning **已启用** | ✅ 推送时自动识别已知厂商密钥格式 |
| 推送保护 | Push protection **已启用** | ✅ 含密钥的推送会被**直接阻断** |
| Dependabot | 安全更新 / 漏洞告警 / 自动修复 PR **已启用** | ✅ 依赖漏洞可自动发现 |
| 私密漏洞上报 | **已启用** | ✅ 可通过 GitHub 私密渠道接收漏洞报告 |
| 非提供者模式扫描 / 密钥有效性校验 | 未启用 | ⚠️ 属付费 Secret Protection 能力，公开库免费版不含；如需用本地 `gitleaks` 替代 |
| main 分支保护 | Ruleset `protect-main` **已生效**（禁删除 + 禁强制推送，所有者可绕过） | ✅ 防误删 / 防历史被覆盖 |
| 仓库内敏感文件 | **未发现**（无 keystore / `.env` / `local.properties`；`gradle.properties` 仅 JVM 参数） | ✅ 基线干净 |
| 访问令牌 | OAuth token，scope `gist, read:org, repo`，存于系统 keyring（未落盘明文） | ⚠️ `repo` 权限偏宽，建议改细粒度令牌 |
| 账号二次验证 | 检测为**未开启** | ❌ **P0 待办** |
| 安装包签名 | 当前发布的是 **debug 签名** APK | ❌ **P0 待办**，见 §3.3 |

基线复核命令：

```bash
gh api repos/Feifly688/life-station --jq '.security_and_analysis'
gh api repos/Feifly688/life-station/rulesets
gh api repos/Feifly688/life-station/collaborators
```

---

## 2. 访问权限控制

**原则：能读的人越少、能写的人越少、每种凭据的权限越小，越好。**

1. **保持单一所有者**：当前仅 `Feifly688`。新增协作者前必须确认对方已开启 2FA，角色按最小必要授予（审阅 `triage` / 提交 `write`），**不授予 admin**。
2. **账号二次验证（P0）**：GitHub → Settings → Password and authentication → 启用 Passkey / TOTP，**离线保存恢复码**。
3. **令牌最小化（P1）**：当前 OAuth token 的 `repo` scope 可读写**全部**仓库（含私有库）。建议改为 **fine-grained PAT**：
   - 仅限 `Feifly688/life-station` 一个仓库
   - 权限仅给 `Contents: Read and write`、`Releases: Read and write`、`Metadata: Read`
   - 设置过期时间（如 90 天），到期轮换
4. **凭据存储**：`gh` 凭据保留在系统 keyring（现状 ✅）。**禁止**使用 `gh auth login --insecure-storage`，禁止把 token 写进脚本、`.bashrc`、记事本或聊天记录。
5. **不留后门**：不使用 SSH deploy key 做发布；不新增 Webhook / GitHub App；不使用 Actions secrets 存放签名口令（当前无 Actions secrets ✅，应保持）。
6. **定期审计**（建议每月）：

```bash
gh api repos/Feifly688/life-station/collaborators            # 协作者
gh api repos/Feifly688/life-station/keys                     # 部署密钥
gh api repos/Feifly688/life-station/hooks                    # Webhook
gh api repos/Feifly688/life-station/actions/secrets          # Actions 密钥
gh api user --jq .two_factor_authentication                   # 2FA 状态
```

---

## 3. 敏感信息保管（密钥 / 签名文件 / 账号密码）

### 3.1 铁律

> **私钥、口令、令牌永不入库；永不出现在命令历史、截图、聊天与公开 issue 中。**

已由 `.gitignore` 明确排除（禁止移除这些规则）：

```
keystore.properties   signing.properties
*.jks  *.keystore  *.p12  *.pfx  *.pem  *.key  *.bks
.env  .env.*  secrets.xml  *.secret
local.properties
```

新增任何含凭据的文件前，**先确认 `.gitignore` 已覆盖**，再 `git add`。

### 3.2 账号与口令

- 一律存入**密码管理器**，不复用、不手写在本地文本文件
- GitHub 账号开启 2FA（P0）
- 令牌按 §2.3 最小化，并记录到期时间

### 3.3 签名密钥：为什么必须弃用 debug 签名（P0）

当前 Release 里发布的是 `app-debug.apk`，由 Android 工具链的 **debug keystore**（`~/.android/debug.keystore`，别名 `androiddebugkey`、口令 `android`）签名。这是一个**公开、固定**的密钥：任何持有同一公开 debug 密钥的人都能造出「同包名、可覆盖安装升级」的 APK，从而冒充你的应用更新。**自测无妨，公开分发不可接受。**

建立正式发布签名：

```bash
# 1) 生成 release keystore（存到仓库目录之外）
keytool -genkeypair -v -keystore "C:\Users\Feiqi\.feiqi-keys\feiqi-release.jks" ^
  -alias feiqi -keyalg RSA -keysize 4096 -validity 10000

# 2) 在 FeiQi/keystore.properties 写入（该文件已在 .gitignore 中，绝不提交）
#    storeFile=C:\\Users\\Feiqi\\.feiqi-keys\\feiqi-release.jks
#    storePassword=<16 位以上随机口令>
#    keyAlias=feiqi
#    keyPassword=<同上或独立口令>

# 3) 用 release 签名构建
gradlew :app:assembleRelease
```

`app/build.gradle.kts` 已按此约定改造：**有 `keystore.properties` 就用发布密钥，没有则自动回退 debug 签名**（保证他人 clone 后仍可构建）。校验签名：

```bash
apksigner verify --print-certs app-release.apk
```

### 3.4 备份硬要求

**签名密钥一旦丢失，将永远无法发布「可覆盖升级」的同包名版本**，用户只能卸载重装（数据丢失）。因此：

- 至少 **2 份离线副本**（加密 U 盘 / 加密压缩包 / 另一台设备的加密目录）
- **keystore 文件与口令分开存放**（文件离线、口令在密码管理器）
- 生成后立即验证备份可还原，并记录生成日期与有效期（示例：10000 天）
- 不要把 keystore 放进任何云盘自动同步目录（避免随同步客户端泄露）

---

## 4. 仓库加密与备份策略

### 4.1 加密

- **传输**：GitHub 全程 HTTPS/TLS ✅。本地 `remote` 必须用 `https://`，勿改成 `http://`。
- **静态**：服务端静态加密由 GitHub 平台负责；但 **`public` 仓库对内容不提供机密性** —— 任何人可读、可 fork。
- 若确需保密（例如后续加入含隐私逻辑或密钥派生实现的代码），只有两条路：
  - **转私有库**：代价是失去免费版的密钥扫描 / 推送保护（需付费 Secret Protection，或改用本地 `gitleaks` 扫描替代）；
  - **仅加密敏感文件**：对公开库意义有限（密文与密钥同时可能泄露），一般不建议。
- 结论：**公开库策略下，「加密」不靠仓库本身，而靠「敏感信息根本不入库」**（§3 铁律）。

### 4.2 备份（建议每次发版后执行）

```bash
# 1) 源码全量镜像（含全部分支/tag；注意：不含 Release 附件）
git clone --mirror https://github.com/Feifly688/life-station.git /backup/life-station.git
git -C /backup/life-station.git fsck --full          # 完整性校验

# 2) Release 附件必须单独备份（mirror 不会带走它们）
gh release download --repo Feifly688/life-station --pattern "*.apk" --dir /backup/apk
sha256sum /backup/apk/*.apk                           # 与 Release 说明中的校验值比对

# 3) 加密后转离线/异地（示例：7z AES-256，加密文件名）
7z a -p -mhe=on life-station-$(date +%F).7z /backup/life-station.git /backup/apk
```

要点：

- **备份必须加密后再离开本机**（U 盘、网盘、移动硬盘）
- 定期做一次**还原演练**（clone 到空目录 + 构建一次），未验证的备份等于没有备份
- 保留策略：最近 3 个版本 APK（现状 ✅）+ **全部 tag 与 Release 说明**（说明里含校验值，属溯源信息）

---

## 5. 操作规范

### 5.1 提交前（每次）

```bash
git status                      # 肉眼确认无密钥/口令类文件
git diff --cached --stat        # 确认改动范围符合预期
git grep -nEi "password|secret|api[_-]?key|token|BEGIN [A-Z ]*PRIVATE KEY" -- .   # 兜底自查
```

可选：本地装 `gitleaks detect --no-git -v` 做提交前扫描（公开库已启用服务端扫描，本地扫描是双保险）。

### 5.2 发版（每次）

1. 三处版本号同步：`app/build.gradle.kts`（versionCode 严格递增 + versionName）、`strings.xml` 的 `version_value`、APK 文件名 `翡栖-vX.Y.Z.apk`
2. 产物须来自 `assembleRelease`（已配置发布签名时）；`assembleDebug` 产物仅限本机自测
3. 上传附件**必须用 ASCII 名** `feiqi-vX.Y.Z.apk`（GitHub 会剥离附件名中的非 ASCII 字符）；Release 标题保持中文
4. Release 说明中附 **SHA-256**（见 §5.3）
5. 滚动保留最近 3 个 APK，旧版不覆盖

### 5.3 产物完整性校验（用户侧）

```bash
sha256sum feiqi-v1.1.3.apk
# e1f9b3d2e3656b709bd4cace929bda99b3f5b58202fd968e2c723c3e04e9ce6f
```

服务端一致性核对（确认线上附件未被替换）：

```bash
gh api repos/Feifly688/life-station/releases/tags/v1.1.3 --jq '.assets[] | {name,size,digest}'
```

### 5.4 明确禁止

- 向 `main` 执行 `git push --force`（ruleset 已拦截；所有者可绕过，**仍须避免**，优先新提交）
- 在公开 issue / PR / Release 说明里粘贴 token、keystore 路径、口令、设备序列号等
- 把 keystore、口令、token 放进云盘同步目录或提交到任何分支（含"临时分支"）
- 在 CI / 第三方网站里填入签名口令

---

## 6. 事故响应（发现泄露后 30 分钟内）

1. **吊销**：GitHub → Settings → Developer settings → 撤销相关 token；修改账号口令
2. **轮换**：
   - 令牌泄露 → 重新签发细粒度令牌并替换本机凭据
   - **签名密钥泄露 → 立即生成新 keystore**，公开产物改用新密钥；评估是否需与用户协商迁移（旧密钥无法撤销，只能弃用）
3. **清理**：已推入库的密钥须 `git filter-repo` 重写历史 + 强制推送，并联系 GitHub Support 清理服务端缓存与 fork（**重写历史并不能让已泄露的密钥"变得安全"，轮换才是根本**）
4. **评估影响**：检查 `gh api repos/Feifly688/life-station/events`、审计日志，确认 Release 附件未被替换（用 §5.3 的 digest 比对）
5. **复盘**：记录时间线，把新发现的防线补进本文档

---

## 7. 待办清单（按优先级）

| 优先级 | 事项 | 状态 |
| --- | --- | --- |
| P0 | 开启 GitHub 账号 2FA（Passkey/TOTP）并保存恢复码 | ⬜ 待办 |
| P0 | 生成 release keystore + 配置 `keystore.properties`，公开产物改用 release 签名 | ⬜ 待办（构建侧已就绪，只差密钥） |
| P1 | 令牌改细粒度 PAT（Contents/Releases 读写 + 限本仓库 + 过期时间） | ⬜ 待办 |
| P1 | 建立备份例程（mirror + Release 附件 + 加密离线） | ⬜ 待办 |
| P2 | 可选：转为私有库（需自建 `gitleaks` 扫描替代免费密钥扫描） | ⬜ 待决策 |
| P2 | 可选：为每个 Release 追加 `SHA256SUMS` 附件 | ⬜ 待决策 |

---

*最后更新：2026-09-21 ｜ 本文档随项目演进持续维护*
