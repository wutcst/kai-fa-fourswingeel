# 杀戮尖塔克隆版 - 开发中

基于 Spring Boot + Vue 3 的卡牌 Roguelike 游戏。

## 前置要求

- Java JDK 17+
- Maven 3.8+
- 浏览器（Chrome / Edge 等）

## 快速启动

```bash
# 1. 启动后端
cd backend
mvn spring-boot:run

# 2. 打开浏览器访问
# http://localhost:8080/cover.html
```

## 项目结构

```
├── backend/                          # Spring Boot 后端
│   └── src/main/java/com/slaythespire/
│       ├── controller/               # REST API 控制器
│       ├── game/
│       │   ├── controller/           # 战斗 API
│       │   ├── model/                # 游戏模型（Card, Player, Enemy）
│       │   │   └── factory/          # 状态工厂
│       │   └── service/              # 战斗服务
│       ├── model/                    # 数据模型（SaveData, EventTemplate）
│       ├── repository/               # 数据仓库（JSON 配置加载）
│       └── service/                  # 业务服务（Event, Save, Question）
│   └── src/main/resources/config/
│       ├── cards.json                # 138 张卡牌定义
│       ├── enemies.json              # 23 种敌人
│       ├── enemy_groups.json         # 25 组敌方阵容
│       ├── events.json               # 20 个随机事件
│       ├── relics.json               # 48 件遗物
│       └── statuses.json             # 14 种状态效果
├── frontend/                         # 纯 HTML/JS 前端
│   ├── cover.html                    # 主菜单 / 角色选择
│   ├── map.html                      # 地图界面
│   ├── fight.html                    # 战斗界面（Vue 3）
│   ├── reward.html                   # 奖励选择
│   ├── shop.html                     # 商店
│   ├── campfire.html                 # 篝火（休息/锻造）
│   ├── question.html                 # 随机事件
│   ├── card_select.html              # 选牌
│   ├── forge.html                    # 锻造（升级卡牌）
│   ├── console.html                  # 调试控制台
│   ├── gameover.html                 # 死亡画面
│   ├── victory.html                  # 通关画面
│   ├── card_ui.js                    # 卡牌 UI 工具库
│   ├── save_helper.js                # 存档读写
│   └── status_bar.js                 # 顶部状态栏 + 弹窗
└── saves/                            # 游戏存档目录
    └── save.json
```

## 游戏机制

### 角色

| 角色 | 初始 HP | 初始遗物 | 卡牌数 |
|------|---------|----------|--------|
| 🦾 铁甲战士 | 80 | 燃烧之血 | 32 张 |
| 🔪 静默猎手 | 70 | 蛇之眼 | 33 张 |

### 卡牌稀有度

| 稀有度 | 数量 | 边框颜色 |
|--------|------|----------|
| 起始 (START) | 7 | 灰 |
| 普通 (COMMON) | 26 | 灰 |
| 罕见 (UNCOMMON) | 19 | 蓝 |
| 稀有 (RARE) | 12 | 橙金 |
| 特殊 (SPECIAL) | 2 | 紫 |

### 特殊机制

| 机制 | 说明 | 示例卡牌 |
|------|------|----------|
| X 耗费 | 消耗所有能量，每点能量一段伤害 | 旋风斩 |
| 多段攻击 | 一次打出 N 段伤害 | 连续拳、飞剑回旋镖 |
| 力量倍率 | 力量加成 ×N | 重刃 (×3/×5) |
| 消耗非攻击牌 | 手中非攻击牌全消耗，每张获格挡 | 重振精神 |
| 伤害=格挡 | 造成当前格挡值的伤害 | 全身撞击 |
| 每攻击获格挡 | 本回合每打出攻击牌获格挡 | 狂怒 |
| 无限锻造 | 篝火处可多次升级，每次 +5 伤害 | 灼热攻击 |
| 小刀 (Shiv) | 0 费 4 伤消耗，由其他牌生成 | 刀刃之舞 |
| 中毒 | 每回合结算伤害后层数 -1 | 致命毒药 |
| 翻倍毒 | 将目标中毒翻倍 | 催化剂 |
| 额外毒结算 | 回合结束中毒额外触发一次 | 触媒 |
| 每抽牌上毒 | 本回合每抽 1 张牌所有敌人 +N 毒 | 腐蚀波 |
| 选择删牌 | 自选删除手中卡牌 | 流浪收藏家 |
| 临时升级 | 本场战斗临时升级手中卡牌 | 武装 |
| 条件打出 | 抽牌堆为空才可打出 | 华丽收场 |

### 状态效果

| 状态 | 效果 |
|------|------|
| 💪 力量 | 攻击伤害 +N |
| 🏃 敏捷 | 格挡 +N |
| ☠️ 毒 | 回合结束造成 N 伤害后 -1 |
| ⚡ 易伤 | 受到的伤害 +50% |
| 💔 虚弱 | 造成的伤害 -25% |
| 🔓 脆弱 | 获得的格挡 -25% |
| 🌿 再生 | 回合结束恢复 N HP 后 -1 |
| 🔮 仪式 | 回合开始获得力量 |
| 👻 无实体 | 受到伤害降为 1 |

## API 接口

### 游戏流程

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/character/{id}` | 获取角色初始数据 |
| POST | `/api/init` | 初始化新游戏存档 |
| GET | `/api/load` | 加载存档 |
| POST | `/api/save` | 保存存档 |
| DELETE | `/api/save` | 删除存档 |
| GET | `/api/map` | 获取/生成地图 |

### 战斗

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/game/new` | 开始新战斗 |
| POST | `/api/game/play` | 打出手牌 |
| POST | `/api/game/endTurn` | 结束回合 |

### 奖励 & 商店

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/reward` | 获取战斗奖励 |
| GET | `/api/cardPool` | 获取可选卡牌池 |
| GET | `/api/bossRelics` | Boss 遗物三选一 |
| GET | `/api/shop` | 获取商店数据 |
| GET | `/api/card/{id}` | 获取单张卡牌 |
| GET | `/api/cards` | 获取所有卡牌 |
| GET | `/api/relics` | 获取所有遗物 |

### 事件

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/event/roll?act={n}` | 按阶段随机事件 |
| POST | `/api/event/choose` | 执行事件选项 |

## 事件分布

| 阶段 | 事件数 | 特点 |
|------|--------|------|
| 第一幕 | 10 | 安全：泉水、牧师、赌局 |
| 第二幕 | 16 | 中等风险：灵魂商人、炼金实验室 |
| 第三幕 | 14 | 高风险：暗影集会（传奇遗物）、虚空裂隙（删 5 张） |

## 开发者功能

- **调试控制台**: 访问 `console.html?char=1` 可自由添加卡牌和遗物
- **卡牌数据**: `GET /api/cards` 返回全部 138 张卡牌
- **存档文件**: `saves/save.json` 可手动编辑

## 技术栈

- **后端**: Spring Boot 2.x + Jackson
- **前端**: 原生 HTML/JS + Vue 3 (仅战斗页面)
- **存储**: JSON 文件存档
- **构建**: Maven

## 许可证

MIT
