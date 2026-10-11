# Neural HUD 4.0.0

<p align="center"><img src="assets/neural-hud-emblem.svg" alt="Símbolo Neural HUD" width="160" /></p>

HUD flutuante Android com uma esfera arrastável e um painel de comandos compacto. A aplicação usa Kotlin nativo, não exige conta nem API de IA e mantém comandos e regras de edição no dispositivo.

## Fluxo principal

1. Abra o app e conceda **Exibir sobre outros apps** quando o Android solicitar.
2. O painel flutuante abre junto da esfera. Toque na esfera para ocultar ou reabrir o painel.
3. Escreva uma instrução e toque em **Salvar e ativar**.
4. Para permitir ações em outros aplicativos, abra **Acessibilidade** e habilite o serviço manualmente nas configurações Android.
5. Arraste a esfera para reposicioná-la; o local fica salvo. **Ajustes** recolhe ou exibe controles de tamanho e opacidade.

Exemplos de instrução suportados:
- `Quando abrir YouTube, pesquise gatos`
- `Quando abrir Chrome, digite https://example.com`
- `Quando abrir YouTube, role para baixo`
- `Quando abrir Chrome, volte`

O motor local reconhece um conjunto limitado de comandos para pesquisar, digitar, tocar em textos acessíveis, voltar, ir para a tela inicial, rolar, aguardar e abrir aplicativos. Ele não gera planos livres nem compreende tudo o que um LLM compreende. Ações que dependem de campos ocultos ou de interfaces personalizadas podem falhar. Campos de senha são ignorados.

## Ferramentas

A tela de ferramentas foi reduzida a três funções:
- **Editar arquivo:** escolha um documento pelo seletor Android, peça uma transformação de texto ou edite manualmente.
- **Pesquisar na Web:** abre uma pesquisa no navegador padrão. Essa pesquisa sai do dispositivo e segue as políticas do site escolhido.
- **Aplicativos lançáveis:** mostra apenas apps que o Android disponibiliza como lançáveis; o app não solicita `QUERY_ALL_PACKAGES`.

### Editor de arquivos

O editor só trabalha com documentos escolhidos explicitamente. Aceita conteúdo textual UTF-8 de até 512 KiB; arquivos binários não são editados. As regras locais suportadas incluem:

- `substitua X por Y`
- `adicionar ao final: texto`
- `adicionar no início: texto`
- `remover linhas contendo X`
- `remover texto: X`
- `substitua tudo por: texto`

Antes da gravação, você pode revisar a proposta e conferir a prévia. A ação de salvar mostra uma confirmação, guarda os bytes anteriores na área privada do app e relê o documento para verificar a gravação. O comando **Restaurar último backup** recupera a cópia mais recente. O Android ou o provedor do documento pode negar a escrita. Backup local não substitui uma cópia separada de arquivos importantes: desinstalar o app ou apagar seus dados remove esses backups.

## Privacidade e autorizações

- A esfera exige a permissão especial de sobreposição; remova-a nas configurações do Android se não quiser que o HUD fique sobre outros apps.
- A automação exige que o usuário habilite manualmente o serviço de Acessibilidade. Esse serviço precisa observar a janela ativa e pode tocar ou inserir texto em controles expostos pela interface acessível. Ative-o somente se confiar no código e compreender esse acesso.
- Não é enviado conteúdo de arquivos a uma API de IA. Instruções e preferências são armazenadas localmente.
- O acesso à Web ocorre apenas quando você aciona a pesquisa.
- O serviço flutuante usa uma notificação persistente enquanto está em execução e oferece uma ação explícita para encerrar.
- O app trata a compatibilidade de áreas seguras do sistema no Android 15/16 e mira API 36. Isso reduz problemas de interface em versões recentes, mas não significa que o Google Play Protect tenha certificado o app.

## Compilação e APK

O workflow `.github/workflows/build-apk.yml` usa JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.10.1 e Android SDK 36. Ele compila e verifica a assinatura do APK com `apksigner`.

- **Debug:** identificador `com.neuralhud.mobile.debug`; serve para teste e pode coexistir com outra instalação do pacote final.
- **Release:** identificador `com.neuralhud.mobile`; o artefato `neural-hud-release-apk` só é gerado quando os segredos da assinatura estão configurados.

Segredos esperados em **Settings → Secrets and variables → Actions**: `NEURALHUD_KEYSTORE_BASE64`, `NEURALHUD_KEYSTORE_PASSWORD`, `NEURALHUD_KEY_ALIAS` e `NEURALHUD_KEY_PASSWORD`. A chave precisa permanecer privada e ser preservada para futuras atualizações do app. Não coloque a chave nem suas senhas em commits públicos.

Abra a aba [Actions](https://github.com/japaguimarassi-create/AdminHubV4/actions) para consultar o estado da compilação e baixar os artefatos. Uma assinatura digital válida verifica integridade e identidade do assinante; não garante ausência de alertas do Android nem aprovação do Play Protect.

## Referências oficiais

- [Diretrizes de design para apps móveis Android](https://developer.android.com/design/ui/mobile)
- [Acessar e editar documentos pelo Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)
- [Tipos de serviços em primeiro plano](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Mudanças de comportamento do Android 16](https://developer.android.com/about/versions/16/behavior-changes-16)
- [Requisitos de API de destino do Google Play](https://support.google.com/googleplay/android-developer/answer/11926878?hl=pt)
