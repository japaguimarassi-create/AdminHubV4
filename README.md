# J.A.R.V.I.S. + Neural HUD 6.0.0

<p align="center"><img src="assets/neural-hud-emblem.svg" alt="Símbolo J.A.R.V.I.S. Neural HUD" width="160" /></p>

Assistente Android local com HUD flutuante, entrada como Assist do sistema, comando de voz offline quando suportado, memória de macros e planos multi-etapas. O roteador e o classificador rodam no dispositivo e não chamam API de IA.

## Como invocar globalmente

1. Instale o APK e abra **J.A.R.V.I.S.**.
2. Toque em **Definir J.A.R.V.I.S. como assistente** e confirme, se o Android oferecer essa escolha.
3. O app também declara um manipulador de `android.intent.action.ASSIST`; se o fabricante usar esse caminho, o comando será aberto numa tela própria com entrada de texto e voz offline.
4. Conceda **Exibir sobre outros apps** se quiser a esfera flutuante.
5. Conceda o microfone apenas quando quiser usar voz. Você pode habilitá-lo no app de configuração ou tocar em **Ouvir offline** na tela de Assist.
6. Em Configurações do telefone, escolha o gesto/botão de Assistente para invocar J.A.R.V.I.S. Esse gesto depende do fabricante e da versão do Android.
7. Para interagir com controles dentro de outros aplicativos, ative manualmente o serviço J.A.R.V.I.S. em **Configurações → Acessibilidade**.

A integração declara um `VoiceInteractionService`, sua `VoiceInteractionSessionService` e um manipulador de `Intent.ACTION_ASSIST`. O Android define os requisitos finais de elegibilidade e o usuário precisa confirmar qualquer mudança de assistente padrão. A existência dessas declarações não garante que todo fabricante exiba J.A.R.V.I.S. no seletor ou use o mesmo gesto de invocação.

**Voz offline:** a sessão tenta usar o reconhecedor de voz *no dispositivo* para `pt-BR`. Funciona apenas quando o sistema tem um provedor/modelo local compatível instalado e o microfone foi permitido. Se não houver reconhecimento local, o comando digitado continua disponível. Não há uma palavra de ativação contínua tipo “Hey JARVIS” implementada nesta versão.

## Comandos locais

O roteador conhece várias formas comuns de pedir:

- **Apps e sites:** `abra o YouTube`, `quero abrir o Chrome`, `abra example.com`, `abra https://example.com`.
- **Pesquisa:** `pesquise gatos`, `procure tutoriais de Kotlin`, `pesquise gatos no YouTube`.
- **Ações no app aberto:** `digite olá`, `toque em Pesquisar`, `role para baixo`, `volte`, `aguarde 2 segundos`.
- **Sistema:** `tela inicial`, `notificações`, `configurações rápidas`, `aplicativos recentes`, `capturar tela`, abrir configurações de Wi‑Fi/Bluetooth/bateria.
- **Mídia e volume:** `aumentar volume`, `diminuir volume`, `silenciar`, `pausar música`.
- **Cálculo e informação local:** `calcule (32 + 45) * 2`, `que horas são`, `qual a data de hoje`.
- **Memória local:** `anote comprar pão`, `mostrar nota`.

Ações globais como voltar/início/notificações, e ações dentro de apps, só funcionam quando as permissões correspondentes foram concedidas e o Android expõe controles acessíveis. Alguns apps usam interfaces que não expõem seus botões/campos; senhas são ignoradas.

## Ensinar um comando novo sem API

A memória permite cadastrar até 100 atalhos locais que disparam ações que o roteador já sabe executar:

```
ensine abrir aula => abra YouTube
```

Depois, escrever **abrir aula** executa a ação ensinada. Também é possível usar `aprenda frase => ação` ou `memorize frase => ação`. O classificador local recebe feedback dessa associação e as macros ficam no armazenamento privado do aplicativo. Uma macro não dá capacidades novas ao Android: ela só encaminha para comandos já suportados. Para evitar ciclos, os atalhos encadeados têm profundidade limitada. Planos que começam abrindo outro app podem executar as etapas seguintes depois que a mudança de tela for detectada. A sequência para se o app não puder ser identificado, um controle estiver inacessível ou uma ação falhar.

## O que significa “2.678 neurônios”

O classificador foi reconstruído com **2.678 unidades ocultas**, 256 características textuais de entrada e uma camada de saída para categorias de intenção. Os pesos são treinados com exemplos pequenos pré-programados e salvos localmente; os atalhos ensinados também alimentam o classificador.

Isso **não é um modelo de linguagem grande (LLM)** e não torna o aplicativo equivalente ao Gemini. Quantidade de neurônios por si só não determina inteligência. Esse classificador estima categorias e ajuda a associar comandos; ele não contém conhecimento geral suficiente para inventar respostas detalhadas ou escrever qualquer código de forma confiável. Não há inferência de um LLM generativo embutido nesta versão.

Quando um pedido não corresponde a uma ação local segura, o app abre uma pesquisa no navegador como alternativa. A consulta é enviada ao site pelo navegador e exige Internet; isso não é uma API de IA nem processamento offline.

## HUD

- Esfera arrastável com posição persistida.
- Painel compacto de comando e estado.
- Ajustes de tamanho e opacidade recolhidos.
- Ferramentas separadas para editar arquivos, pesquisar e listar apps lançáveis.
- Editor de arquivos limitado a texto UTF-8 de até 512 KiB, com prévia, confirmação, backup privado e verificação após gravação.

## Permissões e limites

- **Sobreposição:** permite que a esfera e o painel apareçam sobre outros apps.
- **Acessibilidade:** permite observar a janela ativa e interagir com elementos acessíveis depois de habilitação manual. Ative só se confiar no código e compreender o alcance.
- **Microfone:** solicitado quando você escolhe configurar o assistente; usado na sessão de voz e sujeito à existência de reconhecimento local.
- **Internet:** usada para abrir pesquisa no navegador; não é usada para chamar API de IA.
- O Android pode negar ações globais, alguns gestos, acesso a interfaces de outros apps ou seleção do papel de assistente. O Play Protect pode exibir alertas; compilação/assinatura digital não equivalem a certificação Play Protect.

## Compilar e baixar

O workflow `.github/workflows/build-apk.yml` compila com JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.10.1 e SDK/API 36. Antes de gerar o APK, executa testes unitários do interpretador, verifica a configuração e executa `apksigner`.

- **Debug:** pacote `com.neuralhud.mobile.debug6`, para teste e instalação paralela à versão 5.
- **Release:** pacote `com.neuralhud.mobile`, produzido apenas se os segredos de assinatura estiverem configurados.

Segredos esperados em **Settings → Secrets and variables → Actions**: `NEURALHUD_KEYSTORE_BASE64`, `NEURALHUD_KEYSTORE_PASSWORD`, `NEURALHUD_KEY_ALIAS` e `NEURALHUD_KEY_PASSWORD`. Preserve a chave privada e nunca a comite no repositório público.

Veja o [GitHub Actions](https://github.com/japaguimarassi-create/AdminHubV4/actions) para estado de compilação e artefatos.

## Referências oficiais

- [RoleManager — papel do assistente e consentimento](https://developer.android.com/reference/android/app/role/RoleManager)
- [VoiceInteractionService — serviço de assistente global](https://developer.android.com/reference/android/service/voice/VoiceInteractionService)
- [VoiceInteractionSession — interface do assistente](https://developer.android.com/reference/android/service/voice/VoiceInteractionSession)
- [SpeechRecognizer — reconhecimento local no dispositivo](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)
