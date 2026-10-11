# Neural HUD

Projeto Android nativo experimental com:
- esfera flutuante arrastável e painel sobreposto;
- opções de aparência e comportamento;
- seletor de arquivos do Android, limitado às permissões concedidas;
- serviço de acessibilidade opcional para classificar sinais da interface visível;
- pequena rede neural treinável localmente, sem API de IA;
- pesquisa Google pelo navegador, sem API de pesquisa;
- compilação remota de APK por GitHub Actions.

## Compilação

Abra **Actions → Build Neural HUD APK → Run workflow**. O APK de depuração aparecerá no artefato `neural-hud-debug-apk`.

A compilação exige JDK 17, Android SDK Platform 35, Build Tools 35 e Gradle 8.9; o workflow instala esses componentes no runner.

## Limites

O app não lê livremente os diretórios privados de outros aplicativos. O acesso à interface por Acessibilidade é opcional e precisa ser habilitado manualmente no Android. O modelo incluído é uma rede neural pequena que aprende por feedback; não é um LLM e não substitui uma IA geral.
