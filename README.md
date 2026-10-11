# J.A.R.V.I.S. 7.0.0

Assistente Android nativo com identidade visual própria, esfera de voz animada, entrada como assistente do sistema, comandos e memória locais, e pesquisa Web dentro do próprio aplicativo.

## O que foi refeito

- Identidade do app, ícone e interface com a marca J.A.R.V.I.S.
- Tela principal com histórico de instruções, campo de comando e esfera inferior azul/ciano/violeta.
- A esfera pulsa de forma uniforme em todas as direções durante escuta, processamento e fala.
- Voz de saída pelo sintetizador instalado no Android, priorizando uma voz portuguesa local quando disponível.
- Reconhecimento local em pt-BR quando o Android tem modelo offline instalado.
- Pesquisa Web interna, sem abrir navegador externo nas pesquisas do roteador.
- WebView limitada a HTTPS, JavaScript desativado, acesso a arquivos/conteúdo local desativado e conteúdo misto bloqueado.
- Núcleo local para intenções, comandos do sistema, aplicativos, cálculos, notas e macros.
- APK compilado e verificado pelo GitHub Actions.

## Compilar

O GitHub Actions gera um APK de depuração a cada push para `main`. O pacote debug é `com.jarvis.assistant.debug7`; o pacote de release é `com.jarvis.assistant`.

1. Abra a aba **Actions** do repositório.
2. Abra a execução mais recente com conclusão bem-sucedida de **Build J.A.R.V.I.S. APK**.
3. Em **Artifacts**, baixe `jarvis-android-debug-apk`.

O APK debug usa assinatura de teste do CI; não é um APK Play Store com chave de release permanente.

## Assistente padrão e automação

Toque em **Assistente padrão** e confirme no seletor Android, se o sistema oferecer. A elegibilidade e o gesto de invocação dependem do fabricante.

Para automação entre aplicativos, ative manualmente o serviço em **Configurações → Acessibilidade**. O Android pode restringir a ativação para APKs instalados por download; nas informações do app, procure o menu `⋮` e **Permitir configurações restritas**. Conceda acessibilidade somente se confiar no app.

## Limites reais

O núcleo de 2.678 unidades ocultas é um classificador de intenções, não um grande modelo de linguagem generativo. Aumentar a contagem de unidades não transforma automaticamente esse classificador em IA de raciocínio geral. Pedidos não tratados localmente podem ser pesquisados na Web, enviando a consulta ao DuckDuckGo.

Login Google real e a regra de que o primeiro e-mail registrado globalmente será proprietário exigem um OAuth Client ID configurado e um backend com banco de dados e regra atômica para eleger o primeiro proprietário. Esses itens não estão configurados no projeto atual; este APK não simula autenticação nem propriedade global.

Automação de jogos não é universal. Interfaces gráficas desenhadas em canvas, incluindo partes de jogos Roblox, podem não expor seus elementos à acessibilidade. Este projeto não injeta código, não contorna anticheat e não garante jogar automaticamente por alguém.

## Privacidade

Comandos e notas são locais. A pesquisa Web transmite o termo pesquisado ao mecanismo de busca. A voz depende do sintetizador de fala instalado. Microfone e acessibilidade exigem permissões explícitas. A WebView bloqueia JavaScript e acesso a arquivos locais.
