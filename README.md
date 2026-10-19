<div align="center">

# foxhg

**Um editor de ROM nativo e multiplataforma para Pokémon HeartGold e SoulSilver.**
Edite Pokémon, golpes, itens, treinadores, encontros selvagens, mapas (em 3D), scripts, textos e mais - no Linux, Windows e macOS, sem Wine.

*A native, cross-platform ROM editor for Pokémon HeartGold and SoulSilver. Edit Pokémon, moves, items, trainers, wild encounters, maps (in 3D), scripts, text and more - on Linux, Windows and macOS, no Wine required.*

**[Português (Brasil)](#português-brasil)** · **[English](#english)**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)
![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-4285F4?logo=jetpackcompose&logoColor=white)
![Platforms](https://img.shields.io/badge/Linux%20%7C%20Windows%20%7C%20macOS-desktop-lightgrey)
![Status](https://img.shields.io/badge/status-alpha-orange)
[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-legendaryredfox-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/legendaryredfox)

<img src="docs/images/start-screen.png" alt="foxhg start screen" width="760">

</div>

---

# Português (Brasil)

## O que é o foxhg?

O [DSPRE](https://github.com/DS-Pokemon-Rom-Editor/DSPRE) é o editor de referência para ROMs de Pokémon de Nintendo DS, mas é um
aplicativo C# WinForms que só roda no Windows (ou no Wine). O **foxhg** faz o mesmo trabalho, reconstruído do zero em Kotlin Multiplatform
com Compose Multiplatform, e por isso roda nativamente em qualquer sistema. O código dos formatos fica em uma biblioteca sem interface
(`core`) e é testado contra uma ROM real, byte a byte.

**Alvo atual: Pokémon HeartGold (inglês).** O SoulSilver (inglês) também abre onde o layout é idêntico. O objetivo é cobrir todos os jogos
Pokémon de Nintendo DS e todos os idiomas (Platinum, Diamond, Pearl, outros idiomas de HeartGold e SoulSilver, depois Black, White, Black 2
e White 2), e então Game Boy e Game Boy Advance, mas nada disso é suportado ainda: o HeartGold vem primeiro.

> **O foxhg não inclui nenhum dado de jogo.** Você precisa da sua própria ROM, obtida legalmente. Nenhuma ROM, arquivo extraído ou sprite é
> guardado neste repositório (as capturas de tela mostram só texto e números), e o foxhg nunca modifica a ROM que você abre: *Gerar ROM*
> escreve um arquivo novo.

O idioma padrão da interface do aplicativo é o português do Brasil. Também há inglês, espanhol e chinês simplificado (**Projeto ▾ → Configurações…**).

## Capturas de tela

<table>
  <tr>
    <td><img src="docs/images/moves-editor.png" alt="Editor de golpes" width="440"><br><sub><b>Golpes:</b> dados de batalha, efeito, flags, importação e exportação CSV</sub></td>
    <td><img src="docs/images/scripts-editor.png" alt="Editor de scripts" width="440"><br><sub><b>Scripts:</b> destaque de sintaxe, nomes de flags e variáveis, prévia de som</sub></td>
  </tr>
  <tr>
    <td><img src="docs/images/text-editor.png" alt="Editor de arquivos de texto" width="440"><br><sub><b>Arquivos de texto:</b> todos os bancos de mensagens, busca, códigos de comando</sub></td>
    <td><img src="docs/images/tms-editor.png" alt="Editor de TMs e HMs" width="440"><br><sub><b>TMs e HMs:</b> escolha o golpe de cada disco</sub></td>
  </tr>
</table>

## Recursos

### Editores

| Área | Editores |
|------|----------|
| **Pokémon** | Dados pessoais (stats base, EVs dados, tipos, habilidades, taxa de captura, crescimento, grupos de ovo, itens segurados selvagens, compatibilidade com TM/HM), Learnsets, Evoluções, Golpes de ovo, Iniciais (espécie, nível, item segurado; o rival e o professor acompanham), Trocas no jogo |
| **Golpes e itens** | Golpes (dados de batalha, flags, alcance, dados de concurso), Itens (importação e exportação CSV para ambos), Itens escondidos, TMs e HMs |
| **Treinadores** | Treinadores (cabeçalho, time com itens segurados e golpes personalizados, itens de batalha, *trocar uma espécie em todos os times*), Classes de treinador (gênero, prêmio em dinheiro) |
| **Mundo** | Encontros selvagens (taxas, grama por hora do dia, slots de água e pesca, enxames, slots de rádio, cópia do SoulSilver), Rock Smash (chance de drop e tabelas de itens), Headbutt (tabelas e tiles de árvores), Bug Contest (os quatro conjuntos diários), Cabeçalhos de mapa (todos os campos), Matrizes, Mapas, Eventos |
| **Scripts e texto** | Scripts (uma linguagem de texto com destaque de sintaxe, nomes de flags, variáveis e mensagens, level scripts), Arquivos de texto (todos os bancos de mensagens, busca em todas as mensagens, nomes dos bancos quando conhecidos) |
| **Áudio** | Navegador de sons (sequências SDAT) com player de sequências embutido |
| **Ferramentas** | Arquivos: árvore somente leitura da ROM com tamanhos, offsets, dump hexadecimal e exportação |

### Edição de mapas

- **Visão 2D do mapa:** tipos de tile e colisão, pintar / preencher / pegar, e todos os eventos (NPCs, treinadores, warps, gatilhos, placas)
  desenhados no mapa - selecione, arraste, adicione, duplique e remova direto no canvas.
- **Visão 3D do mapa:** terreno e construções renderizados com as texturas reais por um renderizador por software embutido (sem biblioteca
  de GPU). Gire, incline, mova e dê zoom; clique em uma construção para selecioná-la; sobreposições para eventos, colisão e a superfície
  de caminhada (BDHC).
- **Edição de construções:** adicione, mova (clicando no chão em 3D), duplique e remova construções, escolha um modelo em um navegador com
  os 340 modelos e suas prévias.
- **Visão geral da matriz:** o mundo inteiro como uma única imagem renderizada.

### Imagens e som em todo lugar

Ícones de Pokémon (inclusive ovos e formas), sprites de frente e de costas com alternância de shiny, ícones de itens, sprites de classes de
treinador, sprites de NPCs, miniaturas de mapa e reprodução de sons aparecem ao lado dos ids em todo lugar onde o aplicativo mostra um.

### Fluxo de trabalho

- **Projetos:** um projeto é uma pasta com *apenas as suas mudanças*. A ROM nunca é copiada nem modificada. Salvamento automático, recentes, religar uma ROM que foi movida.
- **Desfazer e refazer** (Ctrl+Z, Ctrl+Shift+Z) em todos os editores, mantidos quando você fecha e reabre um projeto.
- **Gerar ROM** escreve um novo `.nds` (o arquivo anterior fica como `.bak`); **Executar** gera e abre o seu emulador.
- **Tela de arquivos, barras de progresso e tela de carregamento**, temas claro e escuro, navegação por teclado em todas as listas (setas), Ctrl+F abre a aba Pesquisa, confirmação antes de ferramentas que mudam muitos registros de uma vez.

### Por baixo dos panos

- Contêiner NDS, NARC, overlays e ARM9 tratados em Kotlin puro, inclusive a compressão BLZ, então overlays editados e um ARM9 substituído
  são recomprimidos e reconstruídos. Uma geração sem edições é idêntica byte a byte à entrada.
- Parsers para dados pessoais, learnsets, evoluções, treinadores, encontros, cabeçalhos de mapa, matrizes, mapas, eventos, scripts, texto,
  gráficos NSBMD / NSBTX / NCGR / NCLR / NCER e o arquivo de som SDAT. **Todo formato tem um teste de ida e volta contra a ROM real.**

## Instalação

### Download

Os pacotes prontos serão publicados na página de releases. Até lá, compile a partir do código-fonte (abaixo). Os pacotes são gerados por
sistema operacional (veja [Empacotamento](#empacotamento)).

### Compilar a partir do código-fonte

Você precisa do **JDK 26** (o projeto está fixado nele; quem usa o [SDKMAN](https://sdkman.io) roda `sdk env`, que escolhe o JDK pelo `.sdkmanrc`) e
mais nada - o Gradle vem no wrapper.

```bash
git clone https://github.com/legendaryredfox/foxhg.git
cd foxhg
./gradlew :app:run                 # abre o aplicativo
```

Abra a sua ROM em **Projeto ▾ → Abrir ROM sem projeto…**, ou comece um projeto com **Novo projeto…** (recomendado: ele salva o seu trabalho
conforme você edita).

```bash
./gradlew :core:jvmTest            # testes de formatos e modelos, sem interface
./gradlew build                    # tudo, inclusive os testes de interface (precisa de um display)
```

## Usando o foxhg

1. **Novo projeto…** → escolha a ROM e uma pasta. O foxhg guarda nessa pasta apenas os arquivos que você muda.
2. Escolha um editor na barra lateral e mude o que quiser. As edições são salvas automaticamente; **Ctrl+Z** desfaz.
3. **Gerar ROM…** escreve um `.nds` modificado. **Executar** gera um arquivo temporário e abre o seu emulador.

### Executar e o seu emulador

Defina o comando em **Projeto ▾ → Configurações…**; `{rom}` marca onde entra o caminho da ROM (ele é acrescentado ao final se faltar):

```text
desmume {rom}
melonDS {rom}
retroarch -L /usr/lib/x86_64-linux-gnu/libretro/desmume_libretro.so {rom}
```

Um emulador no seu `PATH` é detectado na primeira vez. As configurações ficam em `~/.config/foxhg/settings.properties`.

### Atalhos de teclado

| Atalho | Ação |
|--------|------|
| Ctrl+N / Ctrl+O / Ctrl+Shift+O | Novo projeto / abrir projeto / abrir uma ROM sem projeto |
| Ctrl+S | Salvar (e gravar o histórico de desfazer) |
| Ctrl+B / Ctrl+R | Gerar ROM / Executar |
| Ctrl+Z / Ctrl+Shift+Z ou Ctrl+Y | Desfazer / refazer |
| Ctrl+F | Ir para a aba Pesquisa (achar onde uma flag, variável, item ou treinador é usado) |
| ↑ ↓ PgUp PgDn | Andar pela lista |
| Visão 3D: arrastar / arrastar com o botão direito / Ctrl+roda | Girar e inclinar / mover / zoom |

(No macOS use ⌘ no lugar de Ctrl.)

## Como funciona

```
foxhg/
├── core/    Biblioteca Kotlin Multiplatform, sem interface: contêiner NDS, NARC, overlays, texto, gráficos,
│            som e um pacote por tipo de dado da ROM. Acesso a arquivos e ao sistema só em jvmMain.
├── app/     Aplicativo desktop Compose Multiplatform: uma tela por editor, estado em view models
│            que dependem só do core.
├── docs/    spec.md, plan.md e formats.md (cada offset e peculiaridade confirmados na ROM real).
└── scripts/ ajudantes de captura de tela, teste de cliques ao vivo e teste de boot em emulador; geradores de tabelas.
```

- **As edições são uma camada por cima.** O `RomProject` guarda os seus arquivos alterados sobre a ROM intacta; *Gerar* aplica as edições.
  Os editores leem através do projeto, então sempre enxergam as próprias edições ainda não salvas.
- **Parse → editar dados imutáveis → serializar.** Cada formato é uma data class imutável com `parse(bytes)` e `toBytes()`, e um teste que
  prova que `parse` seguido de `serialize` reproduz os bytes originais de cada entrada da ROM.
- **Documentado.** O [`docs/formats.md`](docs/formats.md) registra cada endereço, offset e peculiaridade encontrados durante a portabilidade,
  inclusive como os dados do mapa 3D (NSBMD, BDHC) são organizados.

### Testes

- Os testes de formatos rodam contra `roms/heartgold.nds` (um link simbólico que você cria; fica fora do git). Sem a ROM eles **são pulados com um aviso**,
  então um clone novo e a CI ainda compilam. `FOXHG_REQUIRE_ROM=1` faz a falta da ROM falhar em vez de pular.
- Os testes de interface usam o Compose UI test contra o conteúdo real da janela e a ROM real, acionando os widgets (cliques, digitação, arrastos). Rode-os com um
  display: `env -u XDG_CURRENT_DESKTOP -u DESKTOP_SESSION -u GNOME_DESKTOP_SESSION_ID xvfb-run -a ./gradlew build`.
- `scripts/live-click-test.sh` roda o aplicativo real no xvfb e o aciona com eventos reais de mouse e teclado do X.
- `scripts/boot-test.sh` inicia uma ROM gerada, sem janela, no RetroArch + DeSmuME, para conferir que as edições ainda chegam à tela de título.

### Empacotamento

Os instaladores nativos vêm do `jpackage` e só podem ser gerados **no sistema operacional de destino**:

| Destino | Comando | Gerado em |
|---------|---------|-----------|
| Debian / Ubuntu | `./gradlew :app:packageDeb` | Linux |
| Instalador do Windows | `./gradlew :app:packageMsi` | Windows (precisa do WiX) |
| Imagem de disco do macOS | `./gradlew :app:packageDmg` | macOS |

O `.deb` é gerado e o seu lançador é conferido no Linux; os pacotes do Windows e do macOS ainda não foram gerados.

## Situação e roteiro

O foxhg está em **alfa**. O HeartGold (inglês) é o foco e a maioria dos editores cobre todos os dados que edita, mas nem tudo foi verificado no
próprio jogo. O que se sabe que não está pronto ou não foi conferido está marcado em [`docs/plan.md`](docs/plan.md), por exemplo:

- A edição de mapas em 3D é feita por etapas: o visualizador, a edição de construções e a exibição da superfície de caminhada funcionam; iluminação, sombras,
  animação de água, placas de superfície inclinadas, rotação e escala, e a escrita de modelos novos ainda faltam.
- A reprodução de sons foi conferida quanto a duração e volume, ainda não de ouvido; alguns recursos do SDAT (streams, portamento, sweeps) são ignorados.
- Ainda não: o editor de Fly, o editor de overlays e a caixa de ferramentas de patches do ARM9, extras de encontros exclusivos do SoulSilver,
  troca do ARM7 e do banner, outros jogos Pokémon.

Especificação: [`docs/spec.md`](docs/spec.md) · Plano e situação: [`docs/plan.md`](docs/plan.md)

## Contribuindo

Issues e pull requests são bem-vindos. Antes de começar em um formato:

1. Veja como os projetos de referência fazem, depois a página do formato, e então escreva o seu próprio código a partir da descrição do formato - **não copie código**
   do DSPRE (AGPL-3.0), do ndspy (GPL-3.0) ou de outros projetos; leia-os para entender um formato e anote o que confirmar em `docs/formats.md`.
   A única exceção são os patches da caixa de ferramentas da ROM, portados do DSPRE sob a AGPL-3.0 (veja [Licença](#licença)).
2. Escreva o parser junto com o seu **teste de ida e volta contra a ROM real**.
3. O `./gradlew build` precisa passar, e nunca faça commit de uma ROM nem de nada extraído de uma.

Olhe antes o código ao redor: uma classe por tipo de arquivo, data classes imutáveis, `core` sem Compose nem AWT.

## Agradecimentos

O foxhg se apoia nos ombros da comunidade de hacking de Pokémon de DS. Obrigado a:

- [DSPRE](https://github.com/DS-Pokemon-Rom-Editor/DSPRE) e seus colaboradores, cujo editor serviu de modelo para este;
- a [documentação de hacking de Pokémon DS](https://ds-pokemon-hacking.github.io/) pelas descrições dos formatos de arquivo;
- [pret/pokeheartgold](https://github.com/pret/pokeheartgold), a decompilação do HeartGold, pelos layouts de structs, constantes e tabelas;
- [scrcmd-database](https://github.com/DS-Pokemon-Rom-Editor/scrcmd-database) (MIT) pelo banco de dados de comandos de script;
- [Pokémon DS Map Studio](https://github.com/Trifindo/Pokemon-DS-Map-Studio), [VSMaker2](https://github.com/Chvlkie/VSMaker2),
  [ndspy](https://github.com/RoadrunnerWMC/ndspy) e [Nds4j](https://github.com/turtleisaac/Nds4j) por documentarem formatos que o foxhg lê.

## Aviso legal

O foxhg é um projeto de fã e não é afiliado nem endossado pela Nintendo, Game Freak, Creatures Inc. ou The Pokémon Company. Pokémon e os nomes
relacionados são marcas registradas de seus donos. O foxhg não contém dados de jogo protegidos por direitos autorais e não ajuda a obtê-los; use-o somente com
uma ROM que você possui e respeite a lei do lugar onde mora.

## Apoio

Se você gosta deste projeto, considere me pagar um café. ☕

[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-legendaryredfox-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/legendaryredfox)

## Licença

O foxhg é software livre sob a **[GNU General Public License v3.0 ou posterior](LICENSE)** (`GPL-3.0-or-later`), com uma parte sob a AGPL:

- Os **patches da caixa de ferramentas da ROM** (`core/src/commonMain/kotlin/io/github/legendaryredfox/foxhg/core/patch/dspre/`) foram portados do
  [DSPRE](https://github.com/DS-Pokemon-Rom-Editor/DSPRE) e continuam sob a **[GNU Affero General Public License v3.0](LICENSES/AGPL-3.0.txt)**
  (`AGPL-3.0-only`), copyright dos colaboradores do DSPRE. A GPL e a AGPL permitem combinar as duas em um só programa. Por causa desses arquivos o
  programa como um todo não pode ser relicenciado para uma licença permissiva (MIT, Apache), a menos que eles sejam removidos ou substituídos. Se você oferecer o foxhg a
  usuários por uma rede, a AGPL pede que você entregue o código-fonte a esses usuários.
- Os dados tirados de outros projetos estão listados com a sua licença em [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Por que a GPL: esta é uma ferramenta em um ecossistema construído sobre ferramentas copyleft (o DSPRE é AGPL-3.0, o ndspy e o Nds4j são GPL-3.0). A GPL mantém o foxhg e
todos os seus forks livres para a comunidade, é compatível com as suas dependências (Kotlin, Compose Multiplatform e kotlinx são Apache-2.0)
e com os dados que ele usa (o banco de scripts em MIT), e permite que qualquer pessoa o use, estude, modifique e compartilhe.

Copyright © 2026 legendaryredfox

---

# English

## What is foxhg?

[DSPRE](https://github.com/DS-Pokemon-Rom-Editor/DSPRE) is the go-to editor for Nintendo DS Pokémon ROMs, but it is a C# WinForms
app that only runs on Windows (or under Wine). **foxhg** has the same job, rebuilt from scratch in Kotlin Multiplatform with
Compose Multiplatform, so it runs natively everywhere. The format code lives in a UI-free library (`core`) and is tested against a real
ROM, byte for byte.

**Current target: Pokémon HeartGold (English).** SoulSilver (English) opens too where the layout is identical. The goal is every
Nintendo DS Pokémon game and language (Platinum, Diamond, Pearl, other HeartGold and SoulSilver languages, later Black, White, Black 2
and White 2), then Game Boy and Game Boy Advance, but none of those is supported yet: HeartGold comes first.

> **foxhg does not include any game data.** You need your own legally obtained ROM. No ROM, extracted file or sprite is ever stored in this
> repository (the screenshots show only text and numbers), and foxhg never modifies the ROM you open: *Build ROM* writes a new file.

## Screenshots

<table>
  <tr>
    <td><img src="docs/images/moves-editor.png" alt="Moves editor" width="440"><br><sub><b>Moves:</b> battle data, effect, flags, CSV import and export</sub></td>
    <td><img src="docs/images/scripts-editor.png" alt="Scripts editor" width="440"><br><sub><b>Scripts:</b> syntax highlighting, flag and variable names, sound preview</sub></td>
  </tr>
  <tr>
    <td><img src="docs/images/text-editor.png" alt="Text archives editor" width="440"><br><sub><b>Text archives:</b> every message bank, search, command codes</sub></td>
    <td><img src="docs/images/tms-editor.png" alt="TMs and HMs editor" width="440"><br><sub><b>TMs and HMs:</b> pick the move behind each disc</sub></td>
  </tr>
</table>

## Features

### Editors

| Area | Editors |
|------|---------|
| **Pokémon** | Personal data (base stats, EV yield, types, abilities, catch rate, growth, egg groups, wild held items, TM/HM compatibility), Learnsets, Evolutions, Egg moves, Starters (species, level, held item; the rival and the professor follow), In-game trades |
| **Moves and items** | Moves (battle data, flags, range, contest data), Items (CSV import and export for both), Hidden items, TMs and HMs |
| **Trainers** | Trainers (header, party with held items and custom moves, battle items, *replace a species in every party*), Trainer classes (gender, prize money) |
| **World** | Wild encounters (rates, grass by time of day, water and fishing slots, swarms, radio slots, copy from SoulSilver), Rock Smash (drop chance and item tables), Headbutt (tables and tree tiles), Bug Contest (the four daily sets), Map headers (every field), Matrices, Maps, Events |
| **Scripts and text** | Scripts (a text language with syntax highlighting, flag, variable and message names, level scripts), Text archives (every message bank, search across all messages, bank names where known) |
| **Audio** | Sounds browser (SDAT sequences) with a built-in sequence player |
| **Tools** | Files: a read-only tree of the ROM with sizes, offsets, a hex dump and export |

### Map editing

- **2D map view:** tile types and collision, paint / fill / pick, and every event (NPCs, trainers, warps, triggers, signs) drawn on the map
  - select, drag, add, duplicate and remove them on the canvas.
- **3D map view:** terrain and buildings rendered with their real textures by a built-in software renderer (no GPU library). Turn, tilt,
  pan and zoom; click a building to select it; overlays for events, collision and the walking surface (BDHC).
- **Building editing:** add, move (click the ground in 3D), duplicate and remove buildings, pick a model from a browser of all 340 models
  with previews.
- **Matrix overview:** the whole overworld as one rendered picture.

### Pictures and sound everywhere

Pokémon icons (including eggs and forms), front and back battle sprites with a shiny switch, item icons, trainer class sprites, overworld
NPC sprites, map thumbnails and sound playback show up next to the ids wherever the app shows one.

### Workflow

- **Projects:** a project is a folder with *only your changes*. The ROM is never copied or modified. Autosave, recents, relink a moved ROM.
- **Undo and redo** (Ctrl+Z, Ctrl+Shift+Z) across all editors, kept when you close and reopen a project.
- **Build ROM** writes a new `.nds` (the previous file is kept as `.bak`); **Run** builds and starts your emulator.
- **Files screen, progress bars and a loading screen**, light and dark themes, keyboard navigation in every list (arrows), Ctrl+F opens the Research tab, a confirmation before tools that change many records at once.

### Under the hood

- NDS container, NARC, overlays and ARM9 handled in pure Kotlin, including BLZ compression, so edited overlays and a replaced ARM9 are
  recompressed and rebuilt. An unedited build is byte-identical to the input.
- Parsers for personal data, learnsets, evolutions, trainers, encounters, map headers, matrices, maps, events, scripts, text, NSBMD / NSBTX /
  NCGR / NCLR / NCER graphics and the SDAT sound archive. **Every format has a round-trip test against the real ROM.**

## Install

### Download

Packaged builds will be published on the release page. Until then, build from source (below). Packages are built per operating system
(see [Packaging](#packaging)).

### Build from source

You need **JDK 26** (the project is pinned to it; [SDKMAN](https://sdkman.io) users: `sdk env` picks it from `.sdkmanrc`) and nothing else -
Gradle comes with the wrapper.

```bash
git clone https://github.com/legendaryredfox/foxhg.git
cd foxhg
./gradlew :app:run                 # launch the app
```

Open your ROM with **Project ▾ → Open ROM without a project…**, or start a project with **New project…** (recommended: it saves your
work as you go).

```bash
./gradlew :core:jvmTest            # format and model tests, no UI
./gradlew build                    # everything, including the UI tests (needs a display)
```

## Using foxhg

1. **New project…** → pick your ROM and a folder. foxhg keeps only the files you change in that folder.
2. Pick an editor in the sidebar and change things. Edits are saved automatically; **Ctrl+Z** undoes.
3. **Build ROM…** writes a patched `.nds`. **Run** builds into a temporary file and launches your emulator.

### Run and your emulator

Set the command in **Project ▾ → Settings…**; `{rom}` marks where the ROM path goes (it is appended when missing):

```text
desmume {rom}
melonDS {rom}
retroarch -L /usr/lib/x86_64-linux-gnu/libretro/desmume_libretro.so {rom}
```

An emulator on your `PATH` is detected the first time. Settings live in `~/.config/foxhg/settings.properties`.

### Keyboard shortcuts

| Shortcut | Action |
|----------|--------|
| Ctrl+N / Ctrl+O / Ctrl+Shift+O | New project / open project / open a ROM without a project |
| Ctrl+S | Save (and write the undo history) |
| Ctrl+B / Ctrl+R | Build ROM / Run |
| Ctrl+Z / Ctrl+Shift+Z or Ctrl+Y | Undo / redo |
| Ctrl+F | Switch to the Research tab (find where a flag, variable, item or trainer is used) |
| ↑ ↓ PgUp PgDn | Move through a list |
| 3D view: drag / right drag / Ctrl+wheel | Turn and tilt / pan / zoom |

(On macOS use ⌘ instead of Ctrl.)

## How it works

```
foxhg/
├── core/    Kotlin Multiplatform library, no UI: NDS container, NARC, overlays, text, graphics,
│            sound and one package per ROM data type. File and OS access only in jvmMain.
├── app/     Compose Multiplatform desktop app: one screen per editor, state in view models
│            that depend only on core.
├── docs/    spec.md, plan.md and formats.md (every offset and quirk confirmed on the real ROM).
└── scripts/ screenshot, live click test and emulator boot test helpers; table generators.
```

- **Edits are an overlay.** `RomProject` holds your changed files on top of the untouched ROM; *Build* applies them. Editors read through
  the project, so they always see their own unsaved edits.
- **Parse → edit immutable data → serialize.** Each format is an immutable data class with `parse(bytes)` and `toBytes()`, and a test that
  proves `parse` then `serialize` reproduces the original bytes for every entry of the ROM.
- **Documented.** [`docs/formats.md`](docs/formats.md) records each address, offset and quirk found while porting, including how
  the 3D map data (NSBMD, BDHC) is laid out.

### Testing

- Format tests run against `roms/heartgold.nds` (a symlink you create; gitignored). Without the ROM they **skip with a notice**, so a fresh
  clone and CI still build. `FOXHG_REQUIRE_ROM=1` makes a missing ROM fail instead.
- UI tests use Compose UI test against the real window content and the real ROM, driving the widgets (clicks, typing, drags). Run them under a
  display: `env -u XDG_CURRENT_DESKTOP -u DESKTOP_SESSION -u GNOME_DESKTOP_SESSION_ID xvfb-run -a ./gradlew build`.
- `scripts/live-click-test.sh` runs the real app under xvfb and drives it with real X mouse and keyboard events.
- `scripts/boot-test.sh` boots a built ROM headless in RetroArch + DeSmuME to check that edits still reach the title screen.

### Packaging

Native installers come from `jpackage` and can only be built **on the operating system they target**:

| Target | Command | Built on |
|--------|---------|----------|
| Debian / Ubuntu | `./gradlew :app:packageDeb` | Linux |
| Windows installer | `./gradlew :app:packageMsi` | Windows (needs WiX) |
| macOS disk image | `./gradlew :app:packageDmg` | macOS |

The `.deb` is built and its launcher is checked on Linux; the Windows and macOS packages have not been built yet.

## Status and roadmap

foxhg is **alpha**. HeartGold (English) is the focus and most editors cover the whole data they edit, but not everything is verified in the
game itself. Things that are known not to be done or not checked are marked in [`docs/plan.md`](docs/plan.md), for example:

- 3D map editing is staged: the viewer, building editing and walking-surface display work; lighting, shadows, water animation, sloped
  surface plates, rotation and scale, and writing new models are still to do.
- Sound playback is checked for length and level, not yet by ear; some SDAT features (streams, portamento, sweeps) are skipped.
- Not yet: the Fly editor, the overlay editor and ARM9 patch toolbox, SoulSilver-only encounter extras,
  replacing the ARM7 and the banner, other Pokémon games.

Spec: [`docs/spec.md`](docs/spec.md) · Plan and status: [`docs/plan.md`](docs/plan.md)

## Contributing

Issues and pull requests are welcome. Before you start on a format:

1. Read how the reference projects do it, then the format page, then write your own code from the format description - **do not copy code**
   from DSPRE (AGPL-3.0), ndspy (GPL-3.0) or other projects; read them to understand a format and note what you confirm in `docs/formats.md`.
   The one exception is the ROM toolbox patches, ported from DSPRE under AGPL-3.0 (see [License](#license)).
2. Write the parser together with its **round-trip test against the real ROM**.
3. `./gradlew build` must pass, and never commit a ROM or anything extracted from one.

Look at the surrounding code first: one class per file type, immutable data classes, `core` free of Compose and AWT.

## Acknowledgements

foxhg stands on the shoulders of the DS Pokémon hacking community. Thank you to:

- [DSPRE](https://github.com/DS-Pokemon-Rom-Editor/DSPRE) and its contributors, whose editor this one is modelled on;
- the [DS Pokémon Hacking documentation](https://ds-pokemon-hacking.github.io/) for the file format descriptions;
- [pret/pokeheartgold](https://github.com/pret/pokeheartgold), the HeartGold decompilation, for struct layouts, constants and tables;
- [scrcmd-database](https://github.com/DS-Pokemon-Rom-Editor/scrcmd-database) (MIT) for the script command database;
- [Pokémon DS Map Studio](https://github.com/Trifindo/Pokemon-DS-Map-Studio), [VSMaker2](https://github.com/Chvlkie/VSMaker2),
  [ndspy](https://github.com/RoadrunnerWMC/ndspy) and [Nds4j](https://github.com/turtleisaac/Nds4j) for documenting formats foxhg reads.

## Disclaimer

foxhg is a fan project and is not affiliated with or endorsed by Nintendo, Game Freak, Creatures Inc. or The Pokémon Company. Pokémon and
related names are trademarks of their owners. foxhg contains no copyrighted game data and does not help with obtaining it; use it only with
a ROM you own, and respect the law where you live.

## Support

If you like this project, please consider buying me a coffee. ☕

[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-legendaryredfox-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/legendaryredfox)

## License

foxhg is free software under the **[GNU General Public License v3.0 or later](LICENSE)** (`GPL-3.0-or-later`), with one part under the AGPL:

- The **ROM toolbox patches** (`core/src/commonMain/kotlin/io/github/legendaryredfox/foxhg/core/patch/dspre/`) are ported from
  [DSPRE](https://github.com/DS-Pokemon-Rom-Editor/DSPRE) and stay under the **[GNU Affero General Public License v3.0](LICENSES/AGPL-3.0.txt)**
  (`AGPL-3.0-only`), copyright the DSPRE contributors. The GPL and the AGPL allow combining the two in one program. Because of those files the
  program as a whole can not be relicensed to a permissive license (MIT, Apache) unless they are removed or replaced. If you offer foxhg to
  users over a network, the AGPL asks you to give those users the source.
- Data taken from other projects is listed with its license in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Why the GPL: this is a tool in an ecosystem built on copyleft tools (DSPRE is AGPL-3.0, ndspy and Nds4j are GPL-3.0). The GPL keeps foxhg and
every fork of it free for the community, is compatible with its dependencies (Kotlin, Compose Multiplatform and kotlinx are Apache-2.0)
and with the data it uses (the MIT script database), and lets anyone use, study, change and share it.

Copyright © 2026 legendaryredfox
