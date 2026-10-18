# Translation glossary

The words foxhg's own windows use all the time, in the four app languages (strings live in `app/src/jvmMain/composeResources/values*`).
Names of Pokémon, moves, items and places are not translated here: they come from the ROM's own text.

| English | Português (Brasil) | Español | 简体中文 |
|---------|--------------------|---------|----------|
| project | projeto | proyecto | 项目 |
| build ROM | gerar ROM | generar ROM | 生成ROM |
| save (a file) | salvar | guardar | 保存 |
| write (a save file) | gravar | escribir | 写入 |
| flag | flag | flag | 标志 |
| variable | variável | variable | 变量 |
| script | script | script | 脚本 |
| event | evento | evento | 事件 |
| overworld object | objeto do mapa | objeto del mapa | 地图物体 |
| warp | warp | warp | 传送点 |
| trigger | gatilho | activador | 触发器 |
| map header | cabeçalho de mapa | cabecera de mapa | 地图头 |
| matrix | matriz | matriz | 矩阵 |
| overlay | overlay | overlay | overlay |
| text archive / bank | arquivo / banco de texto | archivo / banco de texto | 文本档案 / 消息库 |
| wild encounter | Pokémon selvagem | Pokémon salvaje | 野生宝可梦 |
| party | equipe | equipo | 队伍 |
| bag | mochila | mochila | 背包 |
| held item | item segurado | objeto equipado | 携带道具 |
| trainer | treinador | entrenador | 训练家 |
| move | golpe | movimiento | 招式 |
| item | item | objeto | 道具 |
| level | nível | nivel | 等级 |
| TM / HM | MT / MO | MT / MO | TM / HM |
| IV / EV | IV / EV | IV / EV | 个体值 / 努力值 |
| ability | habilidade | habilidad | 特性 |
| nature | natureza | naturaleza | 性格 |
| shiny | shiny | shiny | 闪光 |
| undo / redo | desfazer / refazer | deshacer / rehacer | 撤销 / 重做 |

Rules: keep `ROM`, `HP`, `CSV`, `ARM9`, `NARC`, file names and numbers as they are; keep move, ability and item names in English where the text
names a specific one (`Rock Smash`, `Pickup`); use the game's own word where the game has one in that language (Pokédex, badges). A string that
needs a number uses a plural resource (`plural_*`), never `"word" + "s"`.

Status: English is the source. Brazilian Portuguese is drafted and waits for the author's review; Spanish and Simplified Chinese are drafts
that have not been read by a native speaker (the settings window says so for them).
