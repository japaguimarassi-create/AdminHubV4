# Neural HUD 3.0

<p align="center"><img src="assets/neural-hud-emblem.svg" alt="Neural HUD custom neural-orbit emblem" width="180" /></p>

Neural HUD é um HUD flutuante Android com automação local, ferramentas e edição assistida de arquivos. Não usa API de IA: comandos de edição e ações suportadas são interpretados por regras locais; isso não é um LLM nem inteligência geral.

## HUD e automação

1. Abra o app. Se a permissão de sobreposição ainda não estiver ativa, o Android abre diretamente a página de permissão.
2. Ao voltar depois de permitir, a esfera e o painel de comandos são iniciados automaticamente.
3. No campo de comando, escreva o que fazer e toque em **Salvar e ativar instrução**.
4. Para executar ações em outros apps, habilite o serviço Neural HUD em **Configurações → Acessibilidade**. O Android exige autorização manual.
5. Abra o app-alvo. O interpretador verifica o pacote ativo e tenta executar somente ações reconhecidas.

Exemplos: `Quando abrir YouTube, pesquise gatos`, `Quando abrir Chrome, digite https://example.com`, `Quando abrir YouTube, role para baixo`. A execução depende dos elementos que o app-alvo expõe à Acessibilidade. Campos de senha são ignorados.

## Editor de arquivos

Abra **Ferramentas → Editor de arquivos** e selecione um documento pelo seletor nativo do Android. O app não recebe acesso geral ao armazenamento: o usuário escolhe explicitamente o documento e o provedor pode recusar a escrita.

- Limite de edição: 512 KiB; apenas conteúdo UTF-8 textual, não arquivos binários.
- Comandos locais suportados: `substitua X por Y`, `adicionar ao final: texto`, `adicionar no início: texto`, `remover linhas contendo X`, `remover texto: X` e `substitua tudo por: texto`.
- Também é possível editar o texto manualmente. A proposta e uma prévia são mostradas antes de salvar.
- Para cada gravação, o original é salvo no armazenamento privado do app. A gravação pede confirmação e o resultado é relido para verificação. A opção **Restaurar último backup** recupera o backup mais recente. O backup fica dentro do armazenamento privado do app; apagar os dados/desinstalar o app remove esses backups.
- A edição e a interpretação ocorrem localmente. O conteúdo do arquivo não é enviado a uma API.

## Privacidade e confiança do Android

- Não é um LLM. O classificador neural local é pequeno; ele não gera código arbitrário e não aprende conhecimentos gerais. O editor aplica apenas as transformações de texto listadas acima.
- O app usa o seletor do Android para acesso a documentos e declara visibilidade de apps lançáveis, em vez de pedir acesso irrestrito à lista de pacotes.
- A permissão de sobreposição e o serviço de Acessibilidade são sensíveis. Ative-os apenas se compreender que o HUD pode aparecer sobre outros apps e, quando autorizado, interagir com os elementos acessíveis.
- A assinatura APK é verificada pelo pipeline. Uma assinatura válida confirma integridade e identidade do assinante, mas **não equivale a certificação do Google Play Protect** nem garante que o Android nunca exibirá alertas. A reputação/avaliação do Play Protect depende também de distribuição, comportamento e análise do Google.

## Compilar e baixar

O workflow `.github/workflows/build-apk.yml` roda em GitHub Actions com JDK 17, Gradle 8.9 e Android SDK 35. Ele sempre compila e verifica o APK `debug`, que tem identificador `com.neuralhud.mobile.debug` e serve para teste.

Para gerar um APK instalável de distribuição com identificador `com.neuralhud.mobile`, configure uma chave privada de release **fora do repositório público** como GitHub Actions Secrets com os nomes `NEURALHUD_KEYSTORE_BASE64`, `NEURALHUD_KEYSTORE_PASSWORD`, `NEURALHUD_KEY_ALIAS` e `NEURALHUD_KEY_PASSWORD`. O workflow só compila e publica `neural-hud-release-apk` quando os quatro segredos estão presentes; então verifica a assinatura com `apksigner` e o identificador do pacote. Nunca comite o arquivo de chave nem valores dos segredos no repositório.

O APK `debug` e o APK `release` têm identificadores diferentes; eles podem coexistir. Para atualizar instalações `release`, use sempre a mesma chave privada e incremente `versionCode`. Perder a chave de release impede atualizações sobre instalações assinadas com ela.
